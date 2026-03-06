package com.liubinrui.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liubinrui.constant.CommonConstant;
import com.liubinrui.enums.PictureReviewStatusEnum;
import com.liubinrui.exception.BusinessException;
import com.liubinrui.exception.ErrorCode;
import com.liubinrui.exception.ThrowUtils;
import com.liubinrui.mapper.PictureMapper;
import com.liubinrui.model.dto.picture.ImageInfo;
import com.liubinrui.model.dto.picture.PictureQueryRequest;
import com.liubinrui.model.dto.picture.PictureUploadRequest;
import com.liubinrui.model.dto.picture.UploadResult;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService {

    @Resource
    private UserService userService;
    private SpaceService spaceService;

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
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        List<String> tagList = pictureQueryRequest.getTags();

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
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "space_id", spaceId);
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

        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = new User();
            if (userIdUserListMap.containsKey(userId))
                pictureVO.setUser(UserVO.objToVo(user));
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
    @Transactional
    public PictureVO uploadPicture(MultipartFile file, String imageUrl,
                                   PictureUploadRequest pictureUploadRequest, User loginUser) {
        //1.参数校验
        boolean hasFile = file != null && !file.isEmpty();
        boolean hasUrl = StrUtil.isNotBlank(imageUrl);
        if (!(hasFile || hasUrl)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请上传文件或提供图片 URL，二者选其一");
        }
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
        UploadResult result = hasFile ?
                handleFileUpload(file, loginUser.getId())
                : handleUrlUpload(imageUrl, loginUser.getId());
        //4.获取原图URL（用于下载）
        String presignedUrl = MinioUtil.getPresignedUrl(result.getOriginalObjectName(), 7 * 24 * 3600);
        // 5.获取缩略图 URL（用于前端展示）
        String thumbPresignedUrl = MinioUtil.getPresignedUrl(result.getThumbnailObjectName(), 7 * 24 * 3600);
        // 6.构建实体，把刚才获得的补充进去
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

            // 4. 生成对象入库名
            String originalObjectName = generateObjectName(userId, ext);
            String thumbnailObjectName = generateObjectName(userId, "jpeg"); // 缩略图统一用 .jpeg
            // 5. 上传原图
            try (InputStream originalIs = new ByteArrayInputStream(originalBytes)) {
                MinioUtil.upload(originalObjectName, file.getContentType(), originalIs); //入库的文件名，并非文件自身的名字
            }
            // 6. 上传缩略图（content-type 固定为 image/jpeg）
            try (InputStream thumbIs = new ByteArrayInputStream(thumbBytes)) {
                MinioUtil.upload(originalObjectName, "image/jpeg", thumbIs);
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
            originalImage = ImageIO.read(bis);
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
        try {
            MinioUtil.upload(originalObjectName, contentType, bis);
        } catch (Exception e) {
            log.error("MinIO 原图上传失败: {}", imageUrl, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "原图存储失败");
        }

        // 9. 上传缩略图
        try {
            MinioUtil.upload(thumbnailObjectName, "image/jpeg", ths);
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
        mimeToExt.put("image/jpeg", ".jepg");
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
}
