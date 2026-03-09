package com.liubinrui.model.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceUserDeleteRequest implements Serializable {

    private Long id;

    private Long spaceId;
}
