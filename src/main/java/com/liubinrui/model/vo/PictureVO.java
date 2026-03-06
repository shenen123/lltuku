package com.liubinrui.model.vo;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liubinrui.model.entity.Picture;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Data
public class PictureVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 图片 url
     */
    private String url;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    @TableField("tags") // 数据库字段名从 options 改为 tags
    @JsonIgnore
    private String tagsJson;
    // 内存中使用的 List 字段（不对应数据库，原 options → tags）
    @TableField(exist = false)
    private List<String> tags; // 若标签实体是 QuestionTag，改为 List<QuestionTag>
    // 自动将 tagsJson ↔ tags 双向转换（原 getOptions → getTags）
    public List<String> getTags() { // 返回值类型同步调整
        if (this.tags != null) {
            return this.tags;
        }
        if (this.tagsJson == null || this.tagsJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            this.tags = objectMapper.readValue(
                    this.tagsJson,
                    new TypeReference<List<String>>() {
                    }
            );
            return this.tags;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse tags JSON", e); // 异常信息同步修改
        }
    }

    // 原 setOptions → setTags
    public void setTags(List<String> tags) { // 参数名和类型同步调整
        this.tags = tags;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            this.tagsJson = objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize tags to JSON", e); // 异常信息同步修改
        }
    }

    /**
     * 分类
     */
    private String category;

    /**
     * 文件体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建用户信息
     */
    private UserVO user;
    /**
     * 空间 id
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;

    /**
     * 封装类转对象
     */
    public static Picture voToObj(PictureVO pictureVO) {
        if (pictureVO == null) {
            return null;
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureVO, picture);
        // 类型不同，需要转换
        picture.setTagsJson(JSONUtil.toJsonStr(pictureVO.getTags()));
        return picture;
    }

    /**
     * 对象转封装类
     */
    public static PictureVO objToVo(Picture picture) {
        if (picture == null) {
            return null;
        }
        PictureVO pictureVO = new PictureVO();
        BeanUtils.copyProperties(picture, pictureVO);
        // 类型不同，需要转换
        pictureVO.setTagsJson(picture.getTagsJson());
        return pictureVO;
    }
}

