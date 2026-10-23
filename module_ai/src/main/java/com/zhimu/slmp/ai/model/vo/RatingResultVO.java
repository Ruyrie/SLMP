package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-K09 评价结果
 */
public class RatingResultVO {

    private String rating;
    private String ratedAt;

    @Nullable
    public String getRating() {
        return rating;
    }

    @Nullable
    public String getRatedAt() {
        return ratedAt;
    }

    public void setRating(@Nullable String rating) {
        this.rating = rating;
    }

    public void setRatedAt(@Nullable String ratedAt) {
        this.ratedAt = ratedAt;
    }
}
