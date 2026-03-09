package com.liubinrui.model.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.liubinrui.exception.ErrorCode;
import com.liubinrui.exception.ThrowUtils;
import com.liubinrui.model.vo.PictureVO;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@TableName(value ="picture")
@Data
public class Picture {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String url;

    /**
     * 缩略图 url
     */
    private String thumbUrl;

    private String name;

    private String category;

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

    private Long picSize;

    private Integer picWidth;

    private Integer picHeight;

    /**
     * 图片宽高比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date editTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableField(fill = FieldFill.UPDATE)
    private Date deleteTime;

    /**
     * 状态：0-待审核; 1-通过; 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    private Long reviewerId;

    private Date reviewTime;

    private Long spaceId;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    public Picture objTopicture(PictureVO pictureVO){
        ThrowUtils.throwIf(pictureVO==null, ErrorCode.NOT_FOUND_ERROR);
        Picture picture =new Picture();
        BeanUtils.copyProperties(pictureVO,picture);
        return picture;
    }

}
