package com.aechak.admin.review.response;

import com.aechak.application.review.admin.usecase.result.AdminReviewImageResult;

public record AdminReviewImageResponse(String imageUrl, int sortOrder) {

    public static AdminReviewImageResponse from(AdminReviewImageResult result) {
        return new AdminReviewImageResponse(result.imageUrl(), result.sortOrder());
    }
}
