package br.com.pratoja.pratoja.web;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
@Controller
public class ErrorPageController implements ErrorController {
    @RequestMapping("/error")public String error(HttpServletRequest request,Model model){Object status=request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);model.addAttribute("status",status==null?500:status);return "error";}
}
