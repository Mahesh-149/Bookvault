package org.example.bookvault;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds the app.rag.* keys from application.properties. */
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private int chunkSize = 800;
    private int summaryTopK = 20;
    private int answerTopK = 6;
    private double similarityThreshold = 0.0;

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

    public int getSummaryTopK() { return summaryTopK; }
    public void setSummaryTopK(int summaryTopK) { this.summaryTopK = summaryTopK; }

    public int getAnswerTopK() { return answerTopK; }
    public void setAnswerTopK(int answerTopK) { this.answerTopK = answerTopK; }

    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
}