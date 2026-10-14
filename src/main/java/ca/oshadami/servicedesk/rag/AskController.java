package ca.oshadami.servicedesk.rag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ask")
public class AskController {

    private final AnswerService answers;

    public AskController(AnswerService answers) {
        this.answers = answers;
    }

    @PostMapping
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return answers.ask(request);
    }
}
