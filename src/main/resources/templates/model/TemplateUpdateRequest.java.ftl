package ${packageName}.model.dto.${dataKey};

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ${upperDataKey}UpdateRequest implements Serializable {

    private Long id;

    private String title;

    private String content;

    private List<String> tags;

    private static final long serialVersionUID = 1L;
}