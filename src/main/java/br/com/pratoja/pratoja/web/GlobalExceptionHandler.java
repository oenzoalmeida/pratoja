package br.com.pratoja.pratoja.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(SecurityException.class)
    public ModelAndView notFound() {
        ModelAndView view = new ModelAndView("error");
        view.addObject("status", 404);
        view.setStatus(HttpStatus.NOT_FOUND);
        return view;
    }
}
