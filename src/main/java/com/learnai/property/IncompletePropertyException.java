package com.learnai.property;

import java.util.List;

import com.learnai.audio.TranscriptionResult;

/** The voice description did not contain every required field; the draft can be completed and sent as a form. */
public class IncompletePropertyException extends RuntimeException {

    private final transient TranscriptionResult transcription;
    private final transient PropertyDetails draft;
    private final transient List<FieldProblem> errors;

    public IncompletePropertyException(TranscriptionResult transcription, PropertyDetails draft, List<FieldProblem> errors) {
        super("The voice description is missing required property details");
        this.transcription = transcription;
        this.draft = draft;
        this.errors = errors;
    }

    public TranscriptionResult transcription() {
        return transcription;
    }

    public PropertyDetails draft() {
        return draft;
    }

    public List<FieldProblem> errors() {
        return errors;
    }

    public record FieldProblem(String field, String message) {
    }
}
