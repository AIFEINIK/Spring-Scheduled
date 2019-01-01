package com.scheduled;

import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Feinik
 * @Discription
 * @Data 2018/12/21
 * @Version 1.0.0
 */
@ControllerAdvice
public class BaseController {

    @InitBinder
    public void dateBinder(WebDataBinder binder) {
        System.out.println("BaseController.dateBinder");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        CustomDateEditor dateEditor = new CustomDateEditor(df, true);
        binder.registerCustomEditor(Date.class,dateEditor);
    }

    @ExceptionHandler
    public void gloableException(Exception e) {
        System.out.println("BaseController.gloableException:" + e);
    }
}
