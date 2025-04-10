package com.lamngo.mealsync.presentation.shared;

import lombok.*;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorEntity {
    private String field;
    private String message;
}
