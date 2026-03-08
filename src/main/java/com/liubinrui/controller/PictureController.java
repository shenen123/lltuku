package com.liubinrui.controller;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liubinrui.annotation.AuthCheck;
import com.liubinrui.api.aliyunai.service.Text2Image;
import com.liubinrui.common.BaseResponse;
import com.liubinrui.common.DeleteRequest;
import com.liubinrui.common.ResultUtils;
import com.liubinrui.constant.UserConstant;
import com.liubinrui.enums.PictureReviewStatusEnum;
import com.liubinrui.exception.BusinessException;
import com.liubinrui.exception.ErrorCode;
import com.liubinrui.exception.ThrowUtils;
import com.liubinrui.imagesearch.model.PictureSearchResult;
import com.liubinrui.imagesearch.sub.ImageSearchApiFacade;
import com.liubinrui.model.dto.picture.*;
import com.liubinrui.model.entity.Picture;
import com.liubinrui.model.entity.Space;
import com.liubinrui.model.entity.User;
import com.liubinrui.model.vo.PictureVO;
import com.liubinrui.service.PictureService;
import com.liubinrui.service.SpaceService;
import com.liubinrui.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private Text2Image text2Image;

    @PostMapping("/upload/file")
    public BaseResponse<PictureVO> uploadPictureByFile(
            @RequestPart("file") MultipartFile multipartFile,
            @ParameterObject PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        //1.数据检验
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.NOT_FOUND_ERROR);
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        //2.检验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (space.getSpaceType().equals(0)) {
                // 必须空间创建人才能上传
                if (!loginUser.getId().equals(space.getUserId())) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
                }
            }
            //小组的公共区域
            //TODO
        }
        // 3.上传图片
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, null, pictureUploadRequest, loginUser);
        // 4.补充审核信息
        pictureService.fillReviewParams(PictureVO.voToObj(pictureVO), loginUser);
        return ResultUtils.success(pictureVO);
    }

    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        //1.参数校验
        ThrowUtils.throwIf(pictureUploadRequest == null, ErrorCode.NOT_FOUND_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long spaceId = pictureUploadRequest.getSpaceId();
        String url = pictureUploadRequest.getFileUrl();
        ThrowUtils.throwIf(StrUtil.isEmpty(url), ErrorCode.NOT_FOUND_ERROR);
        //2.空间ID不为零，则检验
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (space.getSpaceType().equals(0)) {
                // 必须空间创建人才能上传
                if (!loginUser.getId().equals(space.getUserId())) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
                }
            }
            //小组的公共区域
            //TODO
        }
        // 3.上传图片
        PictureVO pictureVO = pictureService.uploadPicture(null, url, pictureUploadRequest, loginUser);
        // 4.补充审核信息
        pictureService.fillReviewParams(PictureVO.voToObj(pictureVO), loginUser);
        return ResultUtils.success(pictureVO);
    }


    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long pictureId = deleteRequest.getId();
        Picture oldPicture = pictureService.getById(pictureId);
        //1.数据检验
        ThrowUtils.throwIf(ObjUtil.isEmpty(pictureId) || oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        //2.检测权限
        pictureService.checkPictureAuth(loginUser, oldPicture);
        // 3.开启事务
        transactionTemplate.executeWithoutResult(status -> {
            //3.1移除图片
            boolean result = pictureService.removeById(oldPicture.getId());
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 3.2释放额度
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
        });
        return ResultUtils.success(true);
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    //留一个悬念，到底是不是只有空间创始人才能修改
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Long pictureId = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(pictureId);
        //检验空间是否一致,没有传spaceId,则复原有图片的spaceId
        Long spaceId = pictureUpdateRequest.getSpaceId();
        if (spaceId != null) {
            if (spaceId != oldPicture.getSpaceId())
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间id不一致");
        } else spaceId = oldPicture.getSpaceId();
        Space space = spaceService.getById(spaceId);
        if (space.getSpaceType().equals(0)) {
            // 必须空间创建人才能修改
            if (pictureId != null) {
                ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
                //必须为本人或管理员才可更新
                if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR.NO_AUTH_ERROR);
                }
            }
        }
        //小组的公共区域
        else {
            System.out.println(0);
        }

        //补充参数
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        picture.setTagsJson(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        picture.setSpaceId(spaceId);
        // 数据校验
        pictureService.validPicture(picture, false);
        //补充审核信息
        pictureService.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<PictureVO>> listMyPictureVOByPage(@RequestBody PictureQueryRequest
                                                                       pictureQueryRequest,
                                                               HttpServletRequest request) {
        ThrowUtils.throwIf(pictureQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        pictureQueryRequest.setUserId(loginUser.getId());
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest
            request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 数据校验
        pictureService.validPicture(picture, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }

    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                      HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        //限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            //公共空间的普通用户可以查看过审的
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            //非空间创建者不能查看
            if (!loginUser.getId().equals(space.getUserId()))
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有权限");
        }
        Page<PictureVO> pictureVOPage = pictureService.listPictureVOByPageWithCache(pictureQueryRequest, request);
        return ResultUtils.success(pictureVOPage);
    }

    //以图搜图
    @PostMapping("/search/picture")
    public BaseResponse<List<PictureSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        List<PictureSearchResult> resultList = ImageSearchApiFacade.searchImage(oldPicture.getUrl());
        return ResultUtils.success(resultList);
    }

    //统一设置分类与标签
    @PostMapping("/edit/batch")
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.batchEditPicture(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }

    @PostMapping("/text/image")
    @AuthCheck(mustRole = UserConstant.VIP_ROLE)
    public BaseResponse<String> getImageFromText(String prompt, HttpServletRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(prompt == null, ErrorCode.PARAMS_ERROR);

        // 2. 获取登录用户 (保持原有逻辑)
        User loginUser = userService.getLoginUser(request);

        String imageUrl;
        try {
            // 3. 【关键修改】包裹在 try-catch 中
            imageUrl = text2Image.basicCall(prompt);
        } catch (com.alibaba.dashscope.exception.NoApiKeyException e) {
            // 处理 Key 缺失或无效的情况
            e.printStackTrace(); // 建议打印日志以便排查
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 服务配置错误：API Key 无效或未配置");
        } catch (com.alibaba.dashscope.exception.ApiException e) {
            // 处理 API 调用失败（如网络错误、模型报错、额度不足等）
            e.printStackTrace();
            // 可以根据 e.getMessage() 判断具体错误，这里统一返回系统错误
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片生成失败：" + e.getMessage());
        } catch (Exception e) {
            // 处理其他未知运行时异常
            e.printStackTrace();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统内部错误：" + e.getMessage());
        }

        // 4. 返回成功结果
        return ResultUtils.success(imageUrl);
    }

}
