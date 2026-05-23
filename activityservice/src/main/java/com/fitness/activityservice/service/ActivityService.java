package com.fitness.activityservice.service;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.model.Activity;
import com.fitness.activityservice.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityService {
    private final ActivityRepository repository;
    public ActivityResponse trackActivity(ActivityRequest request) {
        Activity activity=Activity.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .duration(request.getDuration())
                .caloriesBurned(request.getCaloriesBurned())
                .startTime(request.getStartTime())
                .additionalMetrics(request.getAdditionalMetrics())
                .build();
        Activity savedActivity=repository.save(activity);
        return  maptoResponse(activity);
    }
    private ActivityResponse maptoResponse(Activity activity){
         ActivityResponse response=new ActivityResponse();
         response.setId(activity.getId());
         response.setUserId(activity.getUserId());
         response.setType(activity.getType());
         response.setDuration(activity.getDuration());
         response.setCaloriesBurned(activity.getCaloriesBurned());
         response.setStartTime(activity.getStartTime());
         response.setAdditionalMetrics(activity.getAdditionalMetrics());
         response.setCreatedAt(activity.getCreatedAt());
         response.setUpdatedAt(activity.getUpdatedAt());
         return response;
    }
}
