package vn.hoidanit.laptopshop.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller

public class OrderController {
    @GetMapping("/admin/order")
    public String getDashBordController() {

        return "admin/order/show";
    }
}
