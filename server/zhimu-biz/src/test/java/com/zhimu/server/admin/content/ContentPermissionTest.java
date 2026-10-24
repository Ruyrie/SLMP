package com.zhimu.server.admin.content;

import com.zhimu.server.admin.content.controller.ContentCategoryAdminController;
import com.zhimu.server.admin.content.controller.CourseAdminController;
import com.zhimu.server.admin.content.controller.CourseTopicAdminController;
import com.zhimu.server.admin.content.controller.FileAdminController;
import com.zhimu.server.admin.content.controller.KnowledgeAdminController;
import com.zhimu.server.admin.content.controller.KnowledgeTaskAdminController;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** 权限点按管理端-02 与服务端文档：查看 VIEW，写操作 HANDLE。 */
class ContentPermissionTest {

    @Test
    void courseEndpointsUseWaC03Permissions() throws Exception {
        assertPermissions(CourseAdminController.class, "summary", "WA-C03:VIEW");
        assertPermissions(CourseAdminController.class, "list", "WA-C03:VIEW", "WA-C04:VIEW");
        assertPermissions(CourseAdminController.class, "create", "WA-C03:HANDLE");
        assertPermissions(CourseAdminController.class, "update", "WA-C03:HANDLE");
        assertPermissions(CourseAdminController.class, "offShelf", "WA-C03:HANDLE");
        assertPermissions(CourseAdminController.class, "deleteDraft", "WA-C03:HANDLE");
        assertPermissions(CourseAdminController.class, "exportStats", "WA-C03:HANDLE");
    }

    @Test
    void topicAndCategoryEndpointsUseContentPermissions() throws Exception {
        assertPermissions(CourseTopicAdminController.class, "list", "WA-C03:VIEW");
        assertPermissions(CourseTopicAdminController.class, "create", "WA-C03:HANDLE");
        assertPermissions(ContentCategoryAdminController.class, "list", "WA-C03:VIEW", "WA-C04:VIEW");
        assertPermissions(ContentCategoryAdminController.class, "create", "WA-C03:HANDLE", "WA-C04:HANDLE");
    }

    @Test
    void knowledgeEndpointsUseWaC04Permissions() throws Exception {
        assertPermissions(KnowledgeAdminController.class, "list", "WA-C04:VIEW");
        assertPermissions(KnowledgeAdminController.class, "publish", "WA-C04:HANDLE");
        assertPermissions(KnowledgeAdminController.class, "rollback", "WA-C04:HANDLE");
        assertPermissions(KnowledgeTaskAdminController.class, "create", "WA-C04:HANDLE");
    }

    @Test
    void fileEndpointsCoverUploadAndViewScenes() throws Exception {
        assertPermissions(FileAdminController.class, "upload", "WA-C01:HANDLE", "WA-C03:HANDLE", "WA-C04:HANDLE");
        assertPermissions(FileAdminController.class, "completeMultipart", "WA-C03:HANDLE", "WA-C04:HANDLE");
        assertPermissions(FileAdminController.class, "get", "WA-C01:VIEW", "WA-C03:VIEW", "WA-C04:VIEW");
    }

    private static void assertPermissions(Class<?> type, String methodName, String... expected) throws Exception {
        Method method = null;
        for (Method candidate : type.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                method = candidate;
                break;
            }
        }
        assertThat(method).as(type.getSimpleName() + "." + methodName).isNotNull();
        AdminPermission permission = AnnotatedElementUtils.findMergedAnnotation(method, AdminPermission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactlyInAnyOrder(expected);
    }
}
