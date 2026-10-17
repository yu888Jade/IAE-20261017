package com.english.assistant.dto;


import java.util.List;


public class EssayResult {


    private int score;


    private List<String> strengths;


    private List<EssayError> errors;


    private List<String> suggestions;


    private String improvedEssay;



    public int getScore() {

        return score;

    }


    public void setScore(int score) {

        this.score = score;

    }



    public List<String> getStrengths() {

        return strengths;

    }


    public void setStrengths(List<String> strengths) {

        this.strengths = strengths;

    }



    public List<EssayError> getErrors() {

        return errors;

    }


    public void setErrors(List<EssayError> errors) {

        this.errors = errors;

    }



    public List<String> getSuggestions() {

        return suggestions;

    }


    public void setSuggestions(List<String> suggestions) {

        this.suggestions = suggestions;

    }



    public String getImprovedEssay() {

        return improvedEssay;

    }


    public void setImprovedEssay(String improvedEssay) {

        this.improvedEssay = improvedEssay;

    }

}