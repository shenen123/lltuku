package com.liubinrui.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.liubinrui.model.dto.space.analyze.*;
import com.liubinrui.model.entity.Picture;
import com.liubinrui.model.entity.User;
import com.liubinrui.model.vo.space.*;

import java.util.List;

public interface SpaceAnalyzeService {
    /**
     * 检查权限
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser);

    /**
     * 填充查询对象
     * @param spaceAnalyzeRequest
     * @param queryWrapper
     */
    void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper);

    /**
     * 空间使用分析
     * @param spaceUsageAnalyzeRequest
     * @param loginUser
     * @return
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);

    /**
     * 根据图片种类进行分类
     * @param spaceCategoryAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);

    /**
     * 根据标签种类分类
     * @param spaceTagAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);


    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    /**
     * 根据用户一周、一月、一年内上传数量统计前100名
     * @param spaceUserAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);

    /**
     * 获取空间使用排名前10
     * @param spaceRankAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceBankAnalyzeResponse> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);
}
