package ru.drudenko.parsedate.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.drudenko.parsedate.dto.ParseDateRq;
import ru.drudenko.parsedate.dto.PromiseDate;
import ru.drudenko.parsedate.service.PromiseDateExtractor;

import java.util.List;

@RestController
@RequestMapping(value = "/parse-date", produces = MediaType.APPLICATION_JSON_VALUE)
public class ParseDateController {

    private final PromiseDateExtractor promiseDateExtractor;

    public ParseDateController(PromiseDateExtractor promiseDateExtractor) {
        this.promiseDateExtractor = promiseDateExtractor;
    }

    @Operation(description = "Парсинг дат")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<PromiseDate> parseDate(@RequestBody ParseDateRq request) {
        return promiseDateExtractor.extract(request.getText(), request.getBaseDate(), request.isHistory());
    }
}
