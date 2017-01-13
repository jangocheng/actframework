package act.util;

import act.Act;
import act.app.ActionContext;
import act.i18n.I18n;
import act.view.Template;
import act.view.ViewManager;
import com.alibaba.fastjson.JSON;
import org.osgl.$;
import org.osgl.http.H;
import org.osgl.mvc.ErrorPageRenderer;
import org.osgl.mvc.MvcConfig;
import org.osgl.mvc.result.ErrorResult;
import org.osgl.util.C;

import java.util.HashMap;
import java.util.Map;

public class ActErrorPageRender extends ErrorPageRenderer {

    public static final String ARG_ERROR = "_error";

    private volatile Boolean i18n;

    private Map<String, $.Var<Template>> templateCache = new HashMap<>();

    @Override
    protected String renderTemplate(ErrorResult error, H.Format format) {
        ActionContext context = ActionContext.current();
        if (null == context) {
            return null;
        }
        int code = error.statusCode();
        Template t = getTemplate(code, context);
        if (null == t) {
            String errorMsg = error.getMessage();
            if (null == errorMsg) {
                errorMsg = MvcConfig.errorMessage(error.status());
            }
            if (i18n()) {
                String translated = context.i18n(true, errorMsg);
                if (translated == errorMsg) {
                    translated = context.i18n(true, MvcConfig.class, errorMsg);
                }
                errorMsg = translated;
            }
            H.Format accept = context.accept();
            if (H.Format.JSON == accept) {
                Map<String, Object> params = C.newMap("code", code, "message", errorMsg);
                return JSON.toJSONString(params);
            } else if (H.Format.HTML == accept) {
                String header = code + " " + errorMsg;
                String content = "<!DOCTYPE html><html><head><title>"
                        + header
                        + "</title></head><body><h1>"
                        + header + "</h1></body></html>";
                return content;
            } else if (H.Format.XML == accept) {
                String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><error><code>"
                        + code + "</code><message>" + errorMsg + "</message></error>";
                return content;
            } else if (H.Format.CSV == accept) {
                return "code,message\n" + code + "," + errorMsg;
            } else if (H.Format.TXT == accept) {
                return code + " " + errorMsg;
            }
            return null;
        }
        context.renderArg(ARG_ERROR, error);
        return t.render(context);
    }

    private String templatePath(int code, ActionContext context) {
        ErrorTemplatePathResolver resolver = context.config().errorTemplatePathResolver();
        if (null == resolver) {
            resolver = new ErrorTemplatePathResolver.DefaultErrorTemplatePathResolver();
        }
        return resolver.resolve(code, context.accept());
    }

    private Template getTemplate(int code, ActionContext context) {
        H.Format format = context.accept();
        String key = code + "" + format;
        $.Var<Template> templateBag = templateCache.get(key);
        if (null == templateBag) {
            ViewManager vm = Act.viewManager();
            if (null == vm) {
                // unit testing
                return null;
            }
            context.templatePath(templatePath(code, context));
            Template t = vm.load(context);
            templateBag = $.var(t);
            templateCache.put(key, templateBag);
        }
        return templateBag.get();
    }

    private boolean i18n() {
        if (null == i18n) {
            synchronized (this) {
                if (null == i18n) {
                    i18n = Act.appConfig().i18nEnabled();
                }
            }
        }
        return i18n;
    }
}
