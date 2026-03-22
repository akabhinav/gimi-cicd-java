package dev.gimi.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Auto-generates an OpenAPI 3.0 spec from Spring MVC request mappings
 * and serves a Swagger UI page.
 */
@RestController
public class OpenApiConfig {

    private final RequestMappingHandlerMapping handlerMapping;
    private final ObjectMapper mapper;

    public OpenApiConfig(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping, ObjectMapper mapper) {
        this.handlerMapping = handlerMapping;
        this.mapper = mapper;
    }

    @GetMapping(value = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectNode> apiDocs() {
        ObjectNode root = mapper.createObjectNode();

        // OpenAPI version
        root.put("openapi", "3.0.3");

        // Info
        ObjectNode info = root.putObject("info");
        info.put("title", "GIMI CI/CD API");
        info.put("description", "REST API for the GIMI CI/CD pipeline engine");
        info.put("version", "0.1.0");

        // Servers
        ArrayNode servers = root.putArray("servers");
        ObjectNode server = servers.addObject();
        server.put("url", "/");
        server.put("description", "Current server");

        // Security schemes
        ObjectNode components = root.putObject("components");
        ObjectNode securitySchemes = components.putObject("securitySchemes");
        ObjectNode bearerAuth = securitySchemes.putObject("bearerAuth");
        bearerAuth.put("type", "http");
        bearerAuth.put("scheme", "bearer");
        bearerAuth.put("bearerFormat", "JWT");

        // Global security
        ArrayNode security = root.putArray("security");
        ObjectNode secItem = security.addObject();
        secItem.putArray("bearerAuth");

        // Paths
        ObjectNode paths = root.putObject("paths");

        Map<RequestMappingInfo, HandlerMethod> mappings = handlerMapping.getHandlerMethods();
        // Group by path
        Map<String, List<Map.Entry<RequestMappingInfo, HandlerMethod>>> byPath = new TreeMap<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            HandlerMethod hm = entry.getValue();

            // Skip non-controller classes (actuator, error, this controller)
            Class<?> beanType = hm.getBeanType();
            if (!beanType.getPackageName().startsWith("dev.gimi.server.controller")
                    && !beanType.equals(OpenApiConfig.class)) {
                continue;
            }
            // Skip this controller's own endpoints
            if (beanType.equals(OpenApiConfig.class)) {
                continue;
            }

            Set<String> patterns = mappingInfo.getPatternValues();
            for (String pattern : patterns) {
                byPath.computeIfAbsent(pattern, k -> new ArrayList<>()).add(entry);
            }
        }

        for (Map.Entry<String, List<Map.Entry<RequestMappingInfo, HandlerMethod>>> pathEntry : byPath.entrySet()) {
            String path = pathEntry.getKey();
            // Convert Spring path variables {id} to OpenAPI format (already correct)
            ObjectNode pathItem = paths.putObject(path);

            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : pathEntry.getValue()) {
                RequestMappingInfo mappingInfo = entry.getKey();
                HandlerMethod hm = entry.getValue();

                Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
                if (methods.isEmpty()) {
                    methods = Set.of(RequestMethod.GET);
                }

                for (RequestMethod method : methods) {
                    String httpMethod = method.name().toLowerCase();
                    ObjectNode operation = pathItem.putObject(httpMethod);

                    // Tag from controller class
                    String tag = hm.getBeanType().getSimpleName().replace("Controller", "");
                    ArrayNode tags = operation.putArray("tags");
                    tags.add(tag);

                    // Operation ID
                    operation.put("operationId", hm.getMethod().getName());

                    // Summary from method name
                    operation.put("summary", camelToTitle(hm.getMethod().getName()));

                    // Path parameters
                    ArrayNode parameters = null;
                    if (path.contains("{")) {
                        parameters = operation.putArray("parameters");
                        String[] parts = path.split("/");
                        for (String part : parts) {
                            if (part.startsWith("{") && part.endsWith("}")) {
                                String paramName = part.substring(1, part.length() - 1);
                                ObjectNode param = parameters.addObject();
                                param.put("name", paramName);
                                param.put("in", "path");
                                param.put("required", true);
                                ObjectNode schema = param.putObject("schema");
                                schema.put("type", "string");
                            }
                        }
                    }

                    // Query parameters
                    Method javaMethod = hm.getMethod();
                    for (Parameter param : javaMethod.getParameters()) {
                        RequestParam rp = param.getAnnotation(RequestParam.class);
                        if (rp != null) {
                            if (parameters == null) {
                                parameters = operation.putArray("parameters");
                            }
                            ObjectNode qp = parameters.addObject();
                            String name = rp.value().isEmpty() ? param.getName() : rp.value();
                            qp.put("name", name);
                            qp.put("in", "query");
                            qp.put("required", rp.required());
                            ObjectNode schema = qp.putObject("schema");
                            schema.put("type", mapJavaType(param.getType()));
                        }
                    }

                    // Request body
                    for (Parameter param : javaMethod.getParameters()) {
                        if (param.getAnnotation(RequestBody.class) != null) {
                            ObjectNode requestBody = operation.putObject("requestBody");
                            requestBody.put("required", true);
                            ObjectNode content = requestBody.putObject("content");
                            ObjectNode json = content.putObject("application/json");
                            ObjectNode schema = json.putObject("schema");
                            schema.put("type", "object");
                            schema.put("description", param.getType().getSimpleName());
                            break;
                        }
                    }

                    // Responses
                    ObjectNode responses = operation.putObject("responses");
                    ObjectNode ok = responses.putObject("200");
                    ok.put("description", "Success");
                    ObjectNode resContent = ok.putObject("content");
                    ObjectNode resJson = resContent.putObject("application/json");
                    ObjectNode resSchema = resJson.putObject("schema");
                    resSchema.put("type", "object");
                }
            }
        }

        return ResponseEntity.ok(root);
    }

    @GetMapping(value = "/swagger-ui.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> swaggerUi() {
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>GIMI CI/CD - API Documentation</title>
                    <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui.css">
                    <style>
                        body { margin: 0; background: #fafafa; }
                        #swagger-ui .topbar { display: none; }
                    </style>
                </head>
                <body>
                    <div id="swagger-ui"></div>
                    <script src="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-bundle.js"></script>
                    <script>
                        SwaggerUIBundle({
                            url: '/v3/api-docs',
                            dom_id: '#swagger-ui',
                            deepLinking: true,
                            presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset],
                            layout: 'BaseLayout',
                            persistAuthorization: true
                        });
                    </script>
                </body>
                </html>
                """;
        return ResponseEntity.ok(html);
    }

    private String camelToTitle(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append(' ');
            }
            sb.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }

    private String mapJavaType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) return "integer";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) return "number";
        return "string";
    }
}
