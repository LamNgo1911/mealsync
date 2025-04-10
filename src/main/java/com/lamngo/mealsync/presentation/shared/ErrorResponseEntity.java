package com.lamngo.mealsync.presentation.shared;

import lombok.*;

import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponseEntity {
    public Object data = null;
    public List<ErrorEntity> errors;
}
