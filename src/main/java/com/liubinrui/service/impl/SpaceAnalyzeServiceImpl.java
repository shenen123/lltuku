package com.liubinrui.service.impl;

import cn.hutool.core.util.NumberUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.liubinrui.exception.BusinessException;
import com.liubinrui.exception.ErrorCode;
import com.liubinrui.exception.ThrowUtils;
import com.liubinrui.model.dto.space.analyze.*;
import com.liubinrui.model.entity.Picture;
import com.liubinrui.model.entity.Space;
import com.liubinrui.model.entity.User;
import com.liubinrui.model.vo.space.*;
import com.liubinrui.service.PictureService;
import com.liubinrui.service.SpaceAnalyzeService;
import com.liubinrui.service.SpaceService;
import com.liubinrui.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SpaceAnalyzeServiceImpl implements SpaceAnalyzeService {

    @Autowired
    private UserService userService;
    @Autowired
    private SpaceService spaceService;
    @Autowired
    private PictureService pictureService;

    @Override
    public void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        //数据检验
        ThrowUtils.throwIf(spaceAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        //检查权限
        if (spaceAnalyzeRequest.isQueryAll() || spaceAnalyzeRequest.isQueryPublic()) {
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        } else {
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            spaceService.checkSpaceAuth(loginUser, space);
        }
    }

    @Override
    public void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        //根据范围填充查询对象
        if (spaceAnalyzeRequest.isQueryAll()) {
            return;
        }
        if (spaceAnalyzeRequest.isQueryPublic()) {
            queryWrapper.isNull("space_id"); //筛选出为空的，即只要公共的
            return;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("space_id", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");

    }

    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        //根据范围填充查询对象
        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {
            boolean isAdmin = userService.isAdmin(loginUser);
            ThrowUtils.throwIf(!isAdmin, ErrorCode.NO_AUTH_ERROR, "无权访问空间");
            //统计公共空间的资源使用
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            if (!spaceUsageAnalyzeRequest.isQueryAll()) {
                queryWrapper.isNull("spaceId");
            }
            //sleect使用SQL函数
            queryWrapper.select("SUM(pic_size) as total_size", "COUNT(1) as total_count");
            List<Map<String, Object>> maps = pictureService.getBaseMapper().selectMaps(queryWrapper);
            long usedSize = 0;
            long usedCount = 0;
            //获取总大小和总数量
            if (!maps.isEmpty()) {
                //获取第一行数据，但实际也就只有第一行
                Map<String, Object> row = maps.get(0);
                Object sizeobj = row.get("total_size");
                usedSize = (sizeobj == null) ? 0L : Long.parseLong(sizeobj.toString());
                Object countobj = row.get("total_count");
                usedCount = (countobj == null) ? 0L : Long.parseLong(countobj.toString());
            }
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);
            spaceUsageAnalyzeResponse.setUsedCount(usedCount);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            return spaceUsageAnalyzeResponse;
        } else {
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
            //校验权限
            spaceService.checkSpaceAuth(loginUser, space);
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("space_id", spaceId);
            queryWrapper.select("SUM(pic_size) as total_size", "COUNT(1) as total_count");
            List<Map<String, Object>> maps = pictureService.getBaseMapper().selectMaps(queryWrapper);

            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(space.getTotalSize());
            spaceUsageAnalyzeResponse.setUsedCount(space.getTotalCount());
            spaceUsageAnalyzeResponse.setMaxSize(space.getMaxSize());
            spaceUsageAnalyzeResponse.setMaxCount(space.getMaxCount());
            double sizeUsageRatio = NumberUtil.round(space.getTotalSize() *
                    100.0 / space.getMaxSize(), 2).doubleValue();
            double countUsageRatio = NumberUtil.round(space.getTotalCount() *
                    100.0 / space.getMaxCount(), 2).doubleValue();
            spaceUsageAnalyzeResponse.setCountUsageRatio(countUsageRatio);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(sizeUsageRatio);
            return spaceUsageAnalyzeResponse;
        }

    }

    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        //权限检验
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        //根据范围补充查询条件
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);
        queryWrapper.select("category AS category", "COUNT(*) AS count",
                        "SUM(pic_size) AS totalSize")
                .groupBy("category");
        List<Map<String, Object>> maps = pictureService.getBaseMapper().selectMaps(queryWrapper);
        List<SpaceCategoryAnalyzeResponse> responseList = new ArrayList<>();
        if (!maps.isEmpty()) {
            for (Map<String, Object> row : maps) {
                Object categoryobj = row.get("category");
                String category = (categoryobj == null) ? "无种类" : categoryobj.toString();
                Object countobj = row.get("count");
                Long count = (countobj == null) ? 0L : Long.parseLong(countobj.toString());
                Object totalSizeobj = row.get("totalSize");
                Long totalSize = (totalSizeobj == null) ? 0L : Long.parseLong(totalSizeobj.toString());

                responseList.add(new SpaceCategoryAnalyzeResponse(category, count, totalSize));
            }
        }
        return responseList;
    }

    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        //权限检验
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        //根据范围补充查询条件
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);
        queryWrapper.select("pic_size as size");
        List<Object> rawSizes = pictureService.getBaseMapper().selectObjs(queryWrapper);
        // 将 Object 转换为 Long 列表
        List<Long> picSizes = new ArrayList<>();
        for (Object obj : rawSizes) {
            if (obj != null) {
                // 安全转换：数据库返回的可能是 Integer, Long, BigDecimal 等，统一转为 long
                picSizes.add(((Number) obj).longValue());
            }
        }
        long countLess100KB = 0L;
        long count100KB_1MB = 0L;
        long count1MB_5MB = 0L;
        long countMore5MB = 0L;
        long KB = 1024L;
        long MB = 1024L * 1024L;
        for (Long size : picSizes) {
            if (size < 100 * KB) {
                countLess100KB++;
            } else if (size >= 100 * KB && size < 1 * MB) {
                count100KB_1MB++;
            } else if (size >= 1 * MB && size < 5 * MB) {
                count1MB_5MB++;
            } else {
                countMore5MB++;
            }
        }
        List<SpaceSizeAnalyzeResponse> resultList = new ArrayList<>();
        resultList.add(new SpaceSizeAnalyzeResponse("<100KB", countLess100KB));
        resultList.add(new SpaceSizeAnalyzeResponse("100KB-1MB", count100KB_1MB));
        resultList.add(new SpaceSizeAnalyzeResponse("1MB-5MB", count1MB_5MB));
        resultList.add(new SpaceSizeAnalyzeResponse(">5MB", countMore5MB));
        return resultList;
    }

    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        return List.of();
    }

    //查找活跃用户
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        String rangeType = spaceUserAnalyzeRequest.getRangeType();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime;
        switch (rangeType.toLowerCase()) {
            case "week":
                // 获取本周一的日期 (如果今天是周一，则返回今天；如果是周日，则返回6天前)
                LocalDate thisMonday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                startTime = thisMonday.atStartOfDay(); // 设置为 00:00:00
                break;

            case "month":
                // 获取本月1号的日期
                LocalDate firstDayOfMonth = now.toLocalDate().withDayOfMonth(1);
                startTime = firstDayOfMonth.atStartOfDay();
                break;

            case "year":
                // 获取今年1月1号的日期
                LocalDate firstDayOfYear = now.toLocalDate().withDayOfYear(1);
                startTime = firstDayOfYear.atStartOfDay();
                break;

            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间范围类型: " + rangeType);
        }
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("user_id", "COUNT(*) AS uploadCount")
                // 假设图片表有逻辑删除字段 isDelete，确保只统计未删除的
                .eq("is_delete", 0)
                // 核心过滤：创建时间 >= 自然周期起点
                .ge("create_time", startTime)
                // 按用户 ID 分组
                .groupBy("user_id")
                // 按上传数量降序排列
                .orderByDesc("uploadCount")
                // 限制只取前 100 条 (使用 last 拼接原生 SQL)
                .last("LIMIT 100");
        List<Map<String, Object>> resultMaps = pictureService.getBaseMapper().selectMaps(queryWrapper);
        //构建返回结果
        List<SpaceUserAnalyzeResponse> spaceUserAnalyzeResponseList = new ArrayList<>();
        for (Map<String, Object> row : resultMaps) {
            Object userIdObj = row.get("user_id");
            Long userId = (userIdObj == null) ? 0L : Long.parseLong(userIdObj.toString());
            Object countObj = row.get("uploadCount");
            long count = (countObj == null) ? 0L : ((Number) countObj).longValue();
            spaceUserAnalyzeResponseList.add(new SpaceUserAnalyzeResponse(userId, count));
        }
        return spaceUserAnalyzeResponseList;
    }

    @Override
    public List<SpaceBankAnalyzeResponse> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        List<SpaceBankAnalyzeResponse> spaceBankAnalyzeResponseList = new ArrayList<>();
        queryWrapper.select("id", "(total_size * 1.0/max_size) AS sizeRate")
                .orderByDesc("sizeRate")
                .last("LIMIT 100");
        List<Map<String, Object>> resultMaps = spaceService.getBaseMapper().selectMaps(queryWrapper);
        for (Map<String, Object> row : resultMaps) {
            Object spaceIdObj = row.get("id");
            Long spaceId = (spaceIdObj == null) ? 0L : Long.parseLong(spaceIdObj.toString());
            Object sizeRateObj = row.get("sizeRate");
            Double sizeRate = (sizeRateObj == null) ? 0 : Double.parseDouble(sizeRateObj.toString());
            spaceBankAnalyzeResponseList.add(new SpaceBankAnalyzeResponse(spaceId,sizeRate));
        }
        return spaceBankAnalyzeResponseList;
    }
}
