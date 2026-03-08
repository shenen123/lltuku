package com.liubinrui.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.liubinrui.api.aliyunai.service.AiTagService;
import com.liubinrui.api.aliyunai.service.TagCleaner;
import com.liubinrui.constant.CommonConstant;
import com.liubinrui.enums.PictureReviewStatusEnum;
import com.liubinrui.exception.BusinessException;
import com.liubinrui.exception.ErrorCode;
import com.liubinrui.exception.ThrowUtils;
import com.liubinrui.mapper.PictureMapper;
import com.liubinrui.model.dto.picture.*;
import com.liubinrui.model.entity.Picture;
import com.liubinrui.model.entity.Space;
import com.liubinrui.model.entity.User;
import com.liubinrui.model.vo.PictureVO;
import com.liubinrui.model.vo.UserVO;
import com.liubinrui.service.PictureService;
import com.liubinrui.service.SpaceService;
import com.liubinrui.service.UserService;
import com.liubinrui.utils.MinioUtil;
import com.liubinrui.utils.PictureUtils;
import com.liubinrui.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService {

    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private AiTagService aiTagService;
    @Resource
    private TagCleaner tagCleaner;
    @Override
    public void validPicture(Picture picture, boolean add) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        String name = picture.getName();
        // 创建数据时，参数不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isBlank(name), ErrorCode.PARAMS_ERROR);
        }
        if (StringUtils.isNotBlank(name)) {
            ThrowUtils.throwIf(name.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
        }
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        List<String> tagList = pictureQueryRequest.getTags();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime=pictureQueryRequest.getEndEditTime();
        if (StringUtils.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText).or().like("introduction", searchText));
        }

        // JSON 数组查询
        if (CollUtil.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                queryWrapper.like("tags", tag);
            }
        }
        //模糊查询
        queryWrapper.like(StrUtil.isNotEmpty(name), "name", name);
        queryWrapper.like(StrUtil.isNotEmpty(introduction), "introduction", introduction);
        //精确查询
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "user_id", userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(spaceId), "space_id", spaceId);
        queryWrapper.eq(reviewStatus != null && reviewStatus > 0, "review_status", reviewStatus);
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);

        Long userId = picture.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        pictureVO.setUser(userVO);
        return pictureVO;
    }

    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {

        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map
                (picture -> PictureVO.objToVo(picture)).collect(Collectors.toList());

        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).filter(Objects::nonNull) // 过滤 null
                .filter(id -> id != 0).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = new User();
            if (userIdUserListMap.containsKey(userId)) {
                user = userService.getById(userId);
                pictureVO.setUser(UserVO.objToVo(user));
            }

        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
            picture.setReviewMessage("待审核");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PictureVO uploadPicture(MultipartFile file, String imageUrl,
                                   PictureUploadRequest pictureUploadRequest, User loginUser) {
        //1.参数校验
        ThrowUtils.throwIf((file == null && StrUtil.isBlank(imageUrl)),
                ErrorCode.PARAMS_ERROR, "请上传文件或提供图片 URL，二者选其一");
        //2.空间检验
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(ObjUtil.isNotNull(space), ErrorCode.NOT_FOUND_ERROR, "访问空间不存在");
            //2.1 私有的必须是空间创建者
            if (space.getSpaceType() == 0) {
                if (!loginUser.getId().equals(space.getUserId()))
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
            //2.2 检验额度
            if (space.getTotalCount() >= space.getMaxCount())
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数已达上限");
            if (space.getTotalSize() >= space.getMaxSize())
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间存储容量不足");
        }
        //3.公共空间，统一上传
        UploadResult result = (file != null) ?
                handleFileUpload(file, loginUser.getId())
                : handleUrlUpload(imageUrl, loginUser.getId());
        //4.获取原图URL（用于下载）
        String presignedUrl = MinioUtil.getPresignedUrl(result.getOriginalObjectName(), 7 * 24 * 3600);
        String objectKey = result.getOriginalObjectName();
        // 5.获取缩略图 URL（用于前端展示）
        String thumbPresignedUrl = MinioUtil.getPresignedUrl(result.getThumbnailObjectName(), 7 * 24 * 3600);
        // 6.生成标签
        //List<String>beginTags=aiTagService.generateTags(file);
        //List<String>endTags=tagCleaner.cleanTags(beginTags);
        // 7.构建实体，把刚才获得的补充进去
        Picture picture = new Picture();
        picture.setUrl(presignedUrl);
        picture.setThumbUrl(thumbPresignedUrl);
        picture.setName(StrUtil.isNotBlank(pictureUploadRequest.getPicName())
                ? pictureUploadRequest.getPicName()
                : result.getOriginalObjectName());  // 原来没有名字就用入库的文件名
        picture.setPicSize(result.getOriginalSize());
        picture.setPicWidth(result.getOriginalImage().getWidth());
        picture.setPicHeight(result.getOriginalImage().getHeight());
        picture.setPicScale((double) result.getOriginalImage().getWidth() / Math.max(1, result.getOriginalImage().getHeight()));
        picture.setPicFormat("JPG");
        picture.setUserId(loginUser.getId());
        picture.setCreateTime(new Date());
        picture.setEditTime(new Date());
        //picture.setTagsJson(JSONUtil.toJsonStr(endTags));
        if (spaceId != null)
            picture.setSpaceId(spaceId);
        // 7.保存到数据库
        boolean saved = this.save(picture);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "图片信息保存失败");
        // 8.更新空间额度（如果关联了空间）
        if (spaceId != null) {
            boolean updated = spaceService.lambdaUpdate().eq(Space::getId, spaceId)
                    .setSql("totalSize = totalSize + " + result.getOriginalSize())
                    .setSql("totalCount = totalCount + 1")
                    .update();
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "空间额度更新失败");
        }
        return PictureVO.objToVo(picture);
    }

    private UploadResult handleFileUpload(MultipartFile file, Long userId) {
        //1.参数校验
        validateImageFile(file);
        String originalFilename = file.getOriginalFilename();
        String ext = getFileExtension(originalFilename);
        try {
            //1.读取原始字节数组
            byte[] originalBytes = file.getBytes();
            long originalSize = originalBytes.length; // uploadResult.setOriginalSize();
            //2.获取原图信息
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (originalImage == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法解析图片格式");
            }
            ImageInfo origInfo = new ImageInfo();
            origInfo.setHeight(originalImage.getHeight());
            origInfo.setWidth(originalImage.getWidth());

            // 3. 生成缩略图以及获取信息（300px 最大边，JPEG 格式）
            byte[] thumbBytes = PictureUtils.generateThumbnail(originalBytes, 300);
            long thumbSize = thumbBytes.length;
            BufferedImage thumbImage = ImageIO.read(new ByteArrayInputStream(thumbBytes));
            ImageInfo thumbInfo = new ImageInfo();
            thumbInfo.setWidth(thumbImage.getWidth());
            thumbInfo.setHeight(thumbImage.getHeight());

            // 4. 生成对象入Minio库名
            String originalObjectName = generateObjectName(userId, ext);
            String thumbnailObjectName = generateObjectName(userId, "jpeg"); // 缩略图统一用 .jpeg
            // 5. 上传原图
            try (InputStream originalIs = new ByteArrayInputStream(originalBytes)) {
                MinioUtil.upload(originalObjectName, file.getContentType(), originalIs); //入库的文件名，并非文件自身的名字
            }
            // 6. 上传缩略图（content-type 固定为 image/jpeg）
            try (InputStream thumbIs = new ByteArrayInputStream(thumbBytes)) {
                MinioUtil.upload(thumbnailObjectName, "image/jpeg", thumbIs);
            }

            // 7. 构建结果（你需要扩展 UploadResult 构造函数或 setter）
            UploadResult uploadResult = new UploadResult();
            uploadResult.setOriginalObjectName(originalObjectName);
            uploadResult.setThumbnailObjectName(thumbnailObjectName);
            uploadResult.setOriginalSize(originalSize);
            uploadResult.setThumbnailSize(thumbSize);
            uploadResult.setOriginalImage(origInfo);
            uploadResult.setThumbnailImage(thumbInfo);
            return uploadResult;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片处理失败");
        }
    }

    private UploadResult handleUrlUpload(String imageUrl, Long userId) {
        //1.基础校验
        if (StrUtil.isBlank(imageUrl)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 URL 不能为空");
        }
        URL url;
        try {
            url = new URL(imageUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的图片 URL 格式");
        }
        //2.下载图片（带超时和大小限制）
        byte[] bytes;
        String contentType = null;
        try {
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);  //连接5s
            connection.setReadTimeout(10000);    //读取10s
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; ImageFetcher/1.0)");
            // 校验内容类型是图片
            contentType = connection.getContentType();
            if (contentType == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法确定资源类型，请确保 URL 指向图片");
            }
            String mimeType = contentType.toLowerCase().split(";")[0].trim();
            if (!mimeType.startsWith("image/")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "URL返回的内容不是图片，实际类型: " + mimeType);
            }
            // 使用Hutool安全下载（自动限制大小）
            bytes = HttpUtil.downloadBytes(url.toString());
            if (bytes.length == 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "下载的图片内容为空");
            }
            if (bytes.length > 10 * 1024 * 1024) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片大小不能超过10MB");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to download image from URL: {}", imageUrl, e);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "从 URL 下载图片失败，请检查链接是否有效");
        }
        // 4. 解析为 BufferedImage 并校验
        ByteArrayInputStream bis;
        BufferedImage originalImage;
        try {
            bis = new ByteArrayInputStream((bytes));
            originalImage = ImageIO.read(bis);   //会直接读到末尾，导致上传时大小为空
            if (originalImage == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法识别的图片格式");
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片解析失败");
        }
        // 5. 确定文件扩展名
        String ext = getExtensionFromContentType(contentType);
        // 6. 生成缩略图（JPEG 格式，300px）
        byte[] thumbBytes;
        BufferedImage thumbImage;
        ByteArrayInputStream ths;
        try {
            thumbBytes = PictureUtils.generateThumbnail(bytes, 300);
            ths = new ByteArrayInputStream(thumbBytes);
            thumbImage = ImageIO.read(ths);
            if (thumbImage == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "缩略图生成失败");
            }
        } catch (Exception e) {
            log.error("生成缩略图失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片处理失败");
        }
        // 7. 生成对象名
        String originalObjectName = generateObjectName(userId, ext);
        String thumbnailObjectName = generateObjectName(userId, "jpeg");

        //8.上传原图
        try (InputStream uploadOriginalIs = new ByteArrayInputStream(bytes)) {
            MinioUtil.upload(originalObjectName, contentType, uploadOriginalIs);
        } catch (Exception e) {
            log.error("MinIO 原图上传失败: {}", imageUrl, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "原图存储失败");
        }

        // 9. 上传缩略图
        try (InputStream uploadThumbIs = new ByteArrayInputStream(thumbBytes)) {
            MinioUtil.upload(thumbnailObjectName, "image/jpeg", uploadThumbIs);
        } catch (Exception e) {
            log.error("MinIO 缩略图上传失败: {}", imageUrl, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "缩略图存储失败");
        }
        // 10. 构建结果
        UploadResult uploadResult = new UploadResult();
        uploadResult.setOriginalObjectName(originalObjectName);
        uploadResult.setThumbnailObjectName(thumbnailObjectName);
        //uploadResult.setContentType();
        uploadResult.setOriginalSize(bytes.length);
        uploadResult.setThumbnailSize(thumbBytes.length);
        ImageInfo origInfo = new ImageInfo();
        origInfo.setWidth(originalImage.getWidth());
        origInfo.setHeight(originalImage.getHeight());
        uploadResult.setOriginalImage(origInfo);

        ImageInfo thumbInfo = new ImageInfo();
        thumbInfo.setWidth(thumbImage.getWidth());
        thumbInfo.setHeight(thumbImage.getHeight());
        uploadResult.setThumbnailImage(thumbInfo);

        return uploadResult;
    }


    private void validateImageFile(MultipartFile file) {
        // 大小限制：10MB
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片大小不能超过 10MB");
        }
        // 格式校验
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持图片格式上传");
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名无效");
        }
        //提取后缀名，并转小写
        String ext = getFileExtension(filename).toLowerCase();
        if (!Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp").contains(ext)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的图片格式");
        }
    }

    //提取后缀名并改为小写例如 .JPG .jpg
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf(".");
        return (lastDot == -1) ? "" : filename.substring(lastDot);
    }

    private String generateObjectName(Long userId, String extension) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID不能为空且必须大于0");
        }

        // 确保扩展名以 "." 开头
        if (extension == null || extension.isEmpty()) {
            extension = ".jpg";
        } else if (!extension.startsWith(".")) {
            extension = "." + extension;
        }

        // 限制扩展名合法性（防止路径穿越或非法字符）过滤扩展名中所有非字母 / 数字 /.的字符
        extension = extension.replaceAll("[^a-z0-9.]", "");
        if (!extension.matches("^\\.[a-z0-9]{2,5}$")) {  // 校验扩展名格式（必须是.+2-5位字母/数字
            extension = ".jpg"; // fallback
        }
        // 生成 8 位随机字符串（UUID 去掉横线后截取前 8 位），保证随机性
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        // 获取当前时间戳
        long timestamp = System.currentTimeMillis();

        return String.format("public/%d/%d_%s%s", userId, timestamp, randomPart, extension);
    }

    private String getExtensionFromContentType(String contentType) {
        if (contentType == null) {
            return ".jpg";
        }
        // 统一转为小写并去除参数以及去除首尾空格（如 "image/jpeg; charset=UTF-8" → "image/jpeg"）
        String type = contentType.toLowerCase().split(";")[0].trim();
        Map<String, String> mimeToExt = new HashMap<>();
        mimeToExt.put("image/jpeg", ".jpg");
        mimeToExt.put("image/jpg", ".jpg");
        mimeToExt.put("image/png", ".png");
        mimeToExt.put("image/gif", ".gif");
        mimeToExt.put("image/webp", ".webp");
        mimeToExt.put("image/bmp", ".bmp");
        mimeToExt.put("image/tiff", ".tiff");
        mimeToExt.put("image/x-icon", ".ico");
        //  未匹配到，则返回.jpg
        return mimeToExt.getOrDefault(type, ".jpg");
    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Space space = spaceService.getById(spaceId);
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (space.getSpaceType().equals(0)) {
                // 必须空间创建人才能上传
                if (!picture.getUserId().equals(loginUser.getId())) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
                }
            }
            //小组的公共区域，必须为管理员或可读可写才可更新
//            else {
//                boolean result = checkLevelSpaceUser(loginUser, spaceId, 2);
//                if (!result)
//                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
        }
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.NOT_FOUND_ERROR);
        //1.构造搜索词
        String searchText = pictureUploadByBatchRequest.getSearchText();
        if (StrUtil.isBlank(searchText)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "搜索关键词不能为空");
        }
        // 2.指定数量
        Integer count = Math.min(pictureUploadByBatchRequest.getCount(), 30);

        // 3.构造 Pexels API URL
        String encodedQuery = URLEncoder.encode(searchText, StandardCharsets.UTF_8);
        String apiUrl = "https://api.pexels.com/v1/search?query=" + encodedQuery + "&per_page=" + count;

        // 4.调用 Pexels API
        HttpResponse response;
        try {
            response = HttpRequest.get(apiUrl)
                    .header("Authorization", "rBA7HZvsESnDgbr3H3KqyE5ilPXN35MJSDiRk8lEmNmWb2uPspMcdZ88")
                    .timeout(10000) // 10秒超时
                    .execute();
        } catch (Exception e) {
            log.error("请求 Pexels API 失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片服务暂时不可用");
        }
        // 6. 解析 JSON 响应（使用 Hutool）
        JSONObject jsonResponse;
        try {
            jsonResponse = JSONUtil.parseObj(response.body()); // ← 替换 Fastjson
        } catch (Exception e) {
            log.error("解析 Pexels 响应失败: {}", response.body(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片服务返回格式异常");
        }
        // 7. 提取图片列表
        JSONArray photos = jsonResponse.getJSONArray("photos");
        if (photos == null || photos.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到相关图片");
        }
        // 8. 批量上传
        int uploadCount = 0;  //记录成功上传多少张
        //如果用户没填名字前缀，就用搜索词作为默认前缀；如果填了，就用用户填的
        String namePrefix = StrUtil.blankToDefault(pictureUploadByBatchRequest.getNamePrefix(), searchText);

        for (int i = 0; i < photos.size() && uploadCount < count; i++) {
            try {
                JSONObject photo = photos.getJSONObject(i);
                JSONObject src = photo.getJSONObject("src");
                // 使用 getStr() 而不是 getString()
                String fileUrl = src.getStr("medium"); // 中等分辨率

                if (StrUtil.isBlank(fileUrl)) {
                    log.warn("跳过空图片 URL: photoId={}", photo.getStr("id"));
                    continue;
                }

                // 构造上传请求
                PictureUploadRequest uploadRequest = new PictureUploadRequest();
                uploadRequest.setPicName(namePrefix + (uploadCount + 1));

                // 执行上传
                PictureVO pictureVO = this.uploadPicture(null, fileUrl, uploadRequest, loginUser);
                log.info("图片上传成功, id = {}, url = {}", pictureVO.getId(), fileUrl);
                uploadCount++;

            } catch (Exception e) {
                log.warn("单张图片上传失败，继续下一张", e);
            }
        }

        return uploadCount;
    }

    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();
    private static final String EMPTY_PAGE_JSON = JSONUtil.toJsonStr(new Page<PictureVO>());

    @Override
    public Page<PictureVO> listPictureVOByPageWithCache(PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        //1.数据检验并构造缓存
        ThrowUtils.throwIf(pictureQueryRequest == null, ErrorCode.NOT_FOUND_ERROR);
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = "lbrpicture:listPictureVOByPage:" + hashKey;

        //2.查询本地缓存
        String cachedValue = LOCAL_CACHE.getIfPresent(hashKey);
        if (cachedValue != null) {
            Page<PictureVO> page = parsePage(cachedValue);
            if (page != null) return page;
        }

        // 3. 查询分布式缓存（Redis）
        RBucket<String> bucket = redissonClient.getBucket(cacheKey);
        cachedValue = bucket.get();
        if (cachedValue != null) {
            LOCAL_CACHE.put(hashKey, cachedValue); // 回填本地缓存
            Page<PictureVO> page = parsePage(cachedValue);
            if (page != null) return page;
        }

        // 4. 【防击穿】加分布式锁
        String lockKey = "lock:picture:list:" + cacheKey;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 5.尝试获取锁（等待1秒，自动释放30秒）
            boolean isLocked = lock.tryLock(1, 30, TimeUnit.SECONDS);
            if (!isLocked) {
                // 获取锁失败：短暂等待后重试（避免大量线程同时失败）
                Thread.sleep(50);
                return listPictureVOByPageWithCache(pictureQueryRequest, request);
            }

            //6.【双重检测】：可能其他线程已重建缓存
            bucket = redissonClient.getBucket(cacheKey);
            cachedValue = bucket.get();
            if (cachedValue != null) {
                Page<PictureVO> page = parsePage(cachedValue);
                return page;
            }

            // 7.都没有，查询数据库
            Page<Picture> picturePage = this.page(new Page<>(pictureQueryRequest.getCurrent(),
                            pictureQueryRequest.getPageSize()),
                    this.getQueryWrapper(pictureQueryRequest));

            Page<PictureVO> pictureVOPage = this.getPictureVOPage(picturePage, request);

            //8.空结果也缓存
            String cacheValue;
            int ttlSeconds;
            if (pictureVOPage == null || pictureVOPage.getRecords().isEmpty()) {
                cacheValue = EMPTY_PAGE_JSON;
                ttlSeconds = 60;
            } else {
                cacheValue = JSONUtil.toJsonStr(pictureVOPage);
                // 【防雪崩】随机 TTL：5～7 分钟
                ttlSeconds = 300 + RandomUtil.randomInt(0, 121);
            }

            // 9. 更新两级缓存
            LOCAL_CACHE.put(hashKey, cachedValue);
            redissonClient.getBucket(cacheKey).set(cacheValue, ttlSeconds, TimeUnit.SECONDS);
            return pictureVOPage;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断", e);
        } finally {
            // 10.释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Page<PictureVO> parsePage(String json) {
        if (json == null || json.isEmpty()) return null;

        try {
            JSONObject root = JSONUtil.parseObj(json);
//            结构如下
//            {
//                "current": 1,
//                    "size": 10,
//                    "total": 100,
//                    "pages": 10,
//                    "records": [ { ... }, { ... } ]
//            }
            Page<PictureVO> page = new Page<>();
            //缺少返回默认值
            page.setCurrent(root.getLong("current", 1L));
            page.setSize(root.getLong("size", 10L));
            page.setTotal(root.getLong("total", 0L));
            page.setPages(root.getLong("pages", 0L));
            // 安全获取records数组，如果不存在，recordsArr 为 null，跳过循环，返回一个空列表的 Page
            JSONArray recordsArr = root.getJSONArray("records");
            List<PictureVO> records = new ArrayList<>();
            if (recordsArr != null) {
                for (Object obj : recordsArr) {
                    // obj 可能是 JSONObject 或 Map
                    PictureVO vo = JSONUtil.toBean(JSONUtil.toJsonStr(obj), PictureVO.class);
                    //先把当前的对象（无论它是 Map 还是 JSONObject）重新序列化回 JSON 字符串
                    //再把这个字符串反序列化成真正的 PictureVO Java 对象。
                    records.add(vo);
                }
            }
            page.setRecords(records);
            return page;
        } catch (Exception e) {
            log.error("解析缓存分页失败", e);
            return null;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }

        // 3. 查询指定图片，仅选择需要的字段
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();

        if (pictureList.isEmpty()) {
            return;
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTagsJson(JSONUtil.toJsonStr(tags));
            }
        });

        // 5. 批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchEditPicture(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1. 参数校验 (保持不变)
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String nameRule = pictureEditByBatchRequest.getNameRule();

        ThrowUtils.throwIf(spaceId == null || CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 2. 校验空间权限 (保持不变)
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }

        // 3. 查询图片 (保持不变)
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();

        fillPictureWithNameRule(pictureList, nameRule);

        if (pictureList.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "指定的图片不存在或不属于该空间");
        }

        // 4. 【核心修改】同步分批处理，确保在同一个事务中
        // 如果任何一批失败，整个方法抛出异常，Spring 会自动回滚所有操作
        int batchSize = 100;
        // 提前准备好要更新的数据，避免在循环中重复判断
        String targetCategory = pictureEditByBatchRequest.getCategory();
        String targetTags = (pictureEditByBatchRequest.getTags() != null)
                ? String.join(",", pictureEditByBatchRequest.getTags())
                : null;

        for (int i = 0; i < pictureList.size(); i += batchSize) {
            List<Picture> batch = pictureList.subList(i, Math.min(i + batchSize, pictureList.size()));

            for (Picture picture : batch) {
                if (targetCategory != null) {
                    picture.setCategory(targetCategory);
                }
                if (targetTags != null) {
                    picture.setTagsJson(targetTags);
                }
            }

            // 执行更新，如果失败会抛出异常，触发 @Transactional 回滚
            boolean result = this.updateBatchById(batch);
            if (!result) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量更新图片失败，第 " + (i/batchSize + 1) + " 批");
            }
        }

        // 方法正常结束，Spring 提交事务
    }

    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

}
