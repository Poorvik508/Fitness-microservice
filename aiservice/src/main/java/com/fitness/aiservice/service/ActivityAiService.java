package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Data
public class ActivityAiService {

    private final GeminiService geminiService;


    public Recommendation generateRecommendation(Activity activity){
        String prompt = createPromptForActivity(activity);
        String aiResponse = geminiService.getAnswer(prompt);
        log.info("Response received from Gemini engine successfully.");
       return processAiResponse(activity,aiResponse);

    }
    private Recommendation processAiResponse(Activity activity,String aiResponse){
        try{
            ObjectMapper mapper=new ObjectMapper();
            JsonNode rootNode=mapper.readTree(aiResponse);
            JsonNode textNode=rootNode.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0).
                    path("text");

            String jsonContent=textNode.asText()
                    .replaceAll("```json\\n","")
                    .replaceAll("\\n```","")
                    .trim();
//            log.info("Parsed response From Ai:{}",jsonContent);

            JsonNode analysisJson=mapper.readTree(jsonContent);
            JsonNode analysisNode=analysisJson.path("analysis");
            StringBuilder fullAnalysis =new StringBuilder();
            addAnalysisSection(fullAnalysis,analysisNode,"overall","Overall");
            addAnalysisSection(fullAnalysis,analysisNode,"pace","Pace");
            addAnalysisSection(fullAnalysis,analysisNode,"heartRate","Heart Rate");
            addAnalysisSection(fullAnalysis,analysisNode,"caloriesBurned","calories:");

            List<String>improvements=extractImprovements(analysisJson.path("improvements"));
            List<String>suggestions=extractSuggestions(analysisJson.path("suggestions"));
            List<String>safety=extractSafety(analysisJson.path("safety"));
            return Recommendation.builder()
                    .activityId(activity.getId())
                    .userId(activity.getUserId())
                    .activityType(activity.getType())
                    .recommendation(fullAnalysis.toString().trim())
                    .improvements(improvements)
                    .suggestions(suggestions)
                    .safety(safety)
                    .createdAt(LocalDateTime.now())
                    .build();
        }catch(Exception e)
        {
            e.printStackTrace();
            return  createDefaultRecommendation(activity);
        }

    }

    private Recommendation createDefaultRecommendation(Activity activity) {
        return  Recommendation.builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .activityType(activity.getType())
                .recommendation("Unable to generate detailed analysis")
                .improvements(Collections.singletonList("Continue with your current routine "))
                .suggestions(Collections.singletonList("Consdier Consulting a fitness professional"))
                .safety(Arrays.asList("Always warm up before exercise",
                        "Stay hyderated",
                        "Listen to your body"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private List<String> extractSafety(JsonNode safetyNode) {
        List<String> safetys = new ArrayList<>();
        if (safetyNode.isArray()) {
            safetyNode.forEach(safety -> safetys.add(safety.asText()));
        }
            return safetys.isEmpty()?
                    Collections.singletonList("Follow General Safety Guideline"):
                    safetys;
    }



    private List<String> extractSuggestions(JsonNode suggestionsNode) {
        List<String> suggestions = new ArrayList<>();
        if (suggestionsNode.isArray()) {
            suggestionsNode.forEach(suggestion -> {
                String workout = suggestion.path("workout").asText();
                String description = suggestion.path("description").asText();
                suggestions.add(String.format("%s: %s", workout, description));

            });
        }
            return suggestions.isEmpty()?
                    Collections.singletonList("No specific suggestions Provided"):
                    suggestions;

    }

    private List<String> extractImprovements(JsonNode improvementsNode) {
        List<String>improvements=new ArrayList<>();
        if(improvementsNode.isArray()) {
            improvementsNode.forEach(improvement -> {
                String area = improvement.path("area").asText();
                String detail = improvement.path("recommendation").asText();
                improvements.add(String.format("%s: %s", area, detail));

            });
        }
            return improvements.isEmpty()?
                    Collections.singletonList("No specific improvements Provided"):
                    improvements;
        }


    private void addAnalysisSection(StringBuilder fullAnalysis, JsonNode analysisNode, String key, String prefix) {
        if(!analysisNode.path(key).isMissingNode())
        {
            fullAnalysis.append(prefix)
                    .append(analysisNode.path(key).asText())
                    .append("\n\n");
        }
    }

    private String createPromptForActivity(Activity activity) {
        // 1. Safely handle the Activity Type String
        String type = (activity.getType() != null) ? activity.getType() : "Workout/Activity";

        // 2. Treat numeric primitives safely (primitives can't be null, so we drop the null-check)
        int duration = activity.getDuration();
        int calories = activity.getCaloriesBurned();

        // 3. FIXED: Explicitly convert metrics to a String first to prevent "Object cannot be converted to String"
        String metrics = "None provided";
        if (activity.getAdditionalMetrics() != null) {
            metrics = String.valueOf(activity.getAdditionalMetrics()).replace("%", " percent");
        }

        return String.format("""
           Analyze this fitness activity and provide detailed recommendations in the following EXACT JSON format:
           {    
             "analysis": {
               "overall": "Overall analysis here",
               "pace": "Pace analysis here",
               "heartRate": "Heart rate analysis here",
               "caloriesBurned": "Calories analysis here"
             },
             "improvements": [
               {
                 "area": "Area name",
                 "recommendation": "Detailed recommendation"
               }
             ],
             "suggestion": [
               {
                 "workout": "workout name",
                 "description": "Detailed workout description"
               }
             ],
             "safety": [
               "Safety point 1",
                 "Safety point 2"
               ]
             }
             
             Analyze this activity details:
             Activity Type: %s
             Duration: %d minutes
             Calories Burned: %d
             Additional Metrics: %s
              
             Provide detailed analysis focusing on performance, improvements, next workout suggestions, and safety guidelines.
             Ensure the response follows the EXACT JSON format shown above. Do not wrap the JSON output inside markdown block code backticks.
             """,
                type, duration, calories, metrics
        );
    }
}