package com.liubinrui.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liubinrui.constant.CommonConstant;
import com.liubinrui.enums.SpaceLevelEnum;
import com.liubinrui.exception.BusinessException;
import com.liubinrui.exception.ErrorCode;
import com.liubinrui.exception.ThrowUtils;
import com.liubinrui.manager.DynamicShardingManager;
import com.liubinrui.mapper.SpaceMapper;
import com.liubinrui.model.dto.space.SpaceAddRequest;
import com.liubinrui.model.dto.space.SpaceQueryRequest;
import com.liubinrui.model.entity.Space;
import com.liubinrui.model.entity.SpaceUser;
import com.liubinrui.model.entity.User;
import com.liubinrui.model.vo.SpaceVO;
import com.liubinrui.model.vo.UserVO;
import com.liubinrui.service.SpaceService;
import com.liubinrui.service.SpaceUserService;
import com.liubinrui.service.UserService;
import com.liubinrui.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService {

    @Autowired
    private UserService userService;

    @Autowired
    private SpaceMapper spaceMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private RedissonClient redissonClient;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    @Lazy
    private SpaceUserService spaceUserService;

    @Resource
    @Lazy
    private DynamicShardingManager dynamicShardingManager;

    @Resource
    private PlatformTransactionManager platformTransactionManager; // 用于开启新事务

    public void checkSpaceLimit(Long userId, Integer targetType) {
        // 1. 构建查询条件
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("space_type", targetType)
                .eq("is_delete", 0);
        // 2. 使用 baseMapper 统计数量
        Long count = spaceMapper.selectCount(queryWrapper);
        // 3. 判断限制
        if (count >= 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "每个用户只能创建一个私有或公共空间");
        }
    }

    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 创建数据时，参数不能为空
        if (add) {
            ThrowUtils.throwIf(space.getSpaceName() == null, ErrorCode.NOT_FOUND_ERROR, "参数不能为空");
            ThrowUtils.throwIf(space.getSpaceLevel() == null, ErrorCode.NOT_FOUND_ERROR, "参数不能为空");
        }
    }

    @Override
    public Long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        Integer spaceType = spaceAddRequest.getSpaceType();
        Long userId = loginUser.getId();
        // 1. 构造分布式锁 Key (基于用户ID，防止同一用户并发创建)  格式：业务前缀:用户ID
        String lockKey = "lock:space:create:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 2. 尝试加锁
            // waitTime: 等待获取锁的最大时间 (1秒)
            // leaseTime: 锁持有时间 (10秒)，超过自动释放，防止死锁
            // 注意：如果业务逻辑执行超过10秒，Redisson 的 WatchDog 机制会自动续期，所以不用担心超时
            boolean isLocked = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (!isLocked) {
                // 获取锁失败，说明有并发请求正在处理
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作过于频繁，请稍后再试");
            }
            // 3. 【核心区域】在锁保护下执行事务
            // 使用 transactionTemplate 手动控制事务范围，确保事务在锁内
            Long spaceId = transactionTemplate.execute(status -> {
                // --- 以下是你原有的业务逻辑 ---
                Integer spaceLevel = spaceAddRequest.getSpaceLevel();
                Space space = new Space();
                BeanUtils.copyProperties(spaceAddRequest, space);
                // 数据校验
                validSpace(space, true);
                space.setUserId(userId);
                // 限制检查 (这是最需要锁保护的地方，防止并发穿透)
                checkSpaceLimit(userId, spaceType);
                // 设置配额
                SpaceLevelEnum levelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
                if (levelEnum == null) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别非法");
                }
                space.setMaxCount(levelEnum.getMaxCount());
                space.setMaxSize(levelEnum.getMaxSize());
                // 写入数据库
                boolean result = this.save(space);
                if (!result) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建空间失败");
                }
                // 关联关系写入 (团队空间/私有空间管理员)
                // 假设 spaceType == 1 代表团队或私有需要加管理员记录
                if (spaceType != null && spaceType == 1) {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceRole("admin");
                    spaceUser.setCreateTime(new Date());
                    spaceUser.setUpdateTime(new Date());
                    boolean saveRelation = spaceUserService.save(spaceUser);
                    if (!saveRelation) {
                        throw new BusinessException(ErrorCode.OPERATION_ERROR, "添加管理员关系失败");
                    }
                }
                return space.getId();
            });
            // 返回结果
            // 现在可以安全地使用 spaceId
//            if (SpaceTypeEnum.TEAM.getValue() == spaceAddRequest.getSpaceType()) {
//                createPictureShardingTable(spaceId); // 使用事务中返回的 ID
//            }
            return spaceId;
        } catch (InterruptedException e) {
            // 加锁过程中线程被中断，恢复中断状态
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙，获取锁失败");
        } finally {
            // 4. 释放锁
            // 关键判断：必须是当前线程持有的锁才能释放，防止误删其他线程的锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


private void createPictureShardingTable(Long spaceId) {
    if (spaceId == null || spaceId <= 0) {
        log.warn("无效的 spaceId，跳过建表: {}", spaceId);
        return;
    }

    String tableName = "picture_" + (spaceId%1024);
    String createTableSql =
            "CREATE TABLE IF NOT EXISTS `" + tableName + "` (" +
                    "`id` BIGINT NOT NULL COMMENT 'id' PRIMARY KEY, " +
                    "`url` VARCHAR(1024) DEFAULT NULL COMMENT '图片 url', " +
                    "`thumb_url` VARCHAR(1024) DEFAULT NULL COMMENT '缩略图预签名 URL', " +
                    "`name` VARCHAR(255) DEFAULT NULL COMMENT '图片名称', " +
                    "`category` VARCHAR(100) DEFAULT NULL COMMENT '分类', " +
                    "`tags` JSON DEFAULT NULL COMMENT '标签（JSON 数组）', " +
                    "`pic_size` BIGINT DEFAULT NULL COMMENT '图片体积', " +
                    "`pic_width` INT DEFAULT NULL COMMENT '图片宽度', " +
                    "`pic_height` INT DEFAULT NULL COMMENT '图片高度', " +
                    "`pic_scale` DOUBLE DEFAULT NULL COMMENT '图片宽高比例', " +
                    "`pic_format` VARCHAR(50) DEFAULT NULL COMMENT '图片格式', " +
                    "`user_id` BIGINT DEFAULT NULL COMMENT '创建用户 id', " +
                    "`create_time` DATETIME DEFAULT CURRENT_TIMESTAMP NULL COMMENT '创建时间', " +
                    "`edit_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '编辑时间', " +
                    "`update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "`delete_time` DATETIME DEFAULT NULL COMMENT '删除时间', " +
                    "`review_status` TINYINT DEFAULT 0 NULL COMMENT '状态：0-待审核; 1-通过; 2-拒绝', " +
                    "`review_message` VARCHAR(1024) DEFAULT NULL COMMENT '审核信息', " +
                    "`reviewer_id` BIGINT DEFAULT NULL COMMENT '审核人 id', " +
                    "`review_time` DATETIME DEFAULT NULL COMMENT '审核时间', " +
                    "`space_id` BIGINT DEFAULT NULL COMMENT '空间 id', " +
                    "`is_delete` TINYINT DEFAULT 0 NOT NULL COMMENT '是否删除', " +
                    // 【重要】补充索引，否则按空间或用户查询会全表扫描
                    "KEY `idx_space_id` (`space_id`), " +
                    "KEY `idx_user_id` (`user_id`), " +
                    "KEY `idx_create_time` (`create_time`) " +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图片分表';";

    try {
        jdbcTemplate.execute(createTableSql);
        log.info("成功创建分表: {}", tableName);

        // 可选：刷新动态分片规则（如果你希望立即生效）
        //dynamicShardingManager.updateShardingTableNodes();
    } catch (Exception e) {
        log.error("创建分表失败: {}", tableName, e);
        // 根据业务决定是否抛异常
        // 如果表不存在会导致后续上传失败，建议抛出异常回滚用户体验
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化空间存储失败，请重试");
    }
}

public void checkSpaceAuth(User user, Space space) {
    //参数检验
    ThrowUtils.throwIf(user == null || space == null, ErrorCode.PARAMS_ERROR, "用户或空间为空");
    //检查是不是空间的创建者
    ThrowUtils.throwIf(!space.getUserId().equals(user.getId()), ErrorCode.NO_AUTH_ERROR);

}

@Override
public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
    QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
    if (spaceQueryRequest == null) {
        return queryWrapper;
    }
    Long id = spaceQueryRequest.getId();
    Long userId = spaceQueryRequest.getUserId();
    String spaceName = spaceQueryRequest.getSpaceName();
    Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
    String sortField = spaceQueryRequest.getSortField();
    String sortOrder = spaceQueryRequest.getSortOrder();
    Integer spaceType = spaceQueryRequest.getSpaceType();

    // 精确查询
    queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
    queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
    queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "space_level", spaceLevel);
    queryWrapper.like(StrUtil.isNotEmpty(spaceName), "space_name", spaceName);
    queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "space_type", spaceType);
    // 排序规则
    queryWrapper.orderBy(SqlUtils.validSortField(sortField),
            sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
            sortField);
    return queryWrapper;
}

@Override
public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
    SpaceVO spaceVO = SpaceVO.objToVo(space);
    Long userId = space.getUserId();
    User user = null;
    if (userId != null && userId > 0) {
        user = userService.getById(userId);
    }
    UserVO userVO = userService.getUserVO(user);
    spaceVO.setUser(userVO);
    return spaceVO;
}

@Override
public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
    List<Space> spaceList = spacePage.getRecords();
    Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
    if (CollUtil.isEmpty(spaceList)) {
        return spaceVOPage;
    }
    // 对象列表 => 封装对象列表
    List<SpaceVO> spaceVOList = spaceList.stream().map
            (SpaceVO::objToVo).collect(Collectors.toList());

    Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
    Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
            .collect(Collectors.groupingBy(User::getId));
    spaceVOList.forEach(spaceVO -> {
        Long userId = spaceVO.getUserId();
        User user = new User();
        if (userIdUserListMap.containsKey(userId))
            spaceVO.setUser(UserVO.objToVo(user));
    });
    spaceVOPage.setRecords(spaceVOList);
    return spaceVOPage;
}


@Override
public boolean isTeamSpace(Long spaceId) {
    ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
    return spaceMapper.selectSpaceType(spaceId) == 1;
}
}
