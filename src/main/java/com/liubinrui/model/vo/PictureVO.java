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

    private Long id;

    private String url;

    private String name;

    @TableField("tags")
    @JsonIgnore
    private String tagsJson;
    @TableField(exist = false)
    private List<String> tags;

    public List<String> getTags() {
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
            throw new RuntimeException("Failed to parse tags JSON", e);
        }
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            this.tagsJson = objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize tags to JSON", e);
        }
    }

    private String category;

    private Long picSize;

    private Integer picWidth;

    private Integer picHeight;

    /**
     * 图片比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    private Long userId;

    private Date createTime;

    private Date editTime;

    private Date updateTime;

    private UserVO user;

    private Long spaceId;

    private static final long serialVersionUID = 1L;

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

