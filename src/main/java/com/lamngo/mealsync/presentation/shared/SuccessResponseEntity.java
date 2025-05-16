package com.lamngo.mealsync.presentation.shared;


import lombok.*;

import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SuccessResponseEntity<T> {
    public T data;
    public Object errors = null;
}
