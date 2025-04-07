#include <linux/fs.h>
#include <linux/namei.h>
#include <linux/module.h>
#include <linux/slab.h>
#include <linux/string.h>
#include <linux/uaccess.h>
#include <linux/version.h>
#include <linux/file.h>
#include <linux/dirent.h>
#include <linux/syscalls.h>
#include "kpm.h"
#include "kpm_compact.h"

#define KPM_MODULE_DIR "/data/adb/kpm"

struct dir_context_impl {
    struct dir_context ctx;
    void *dirent;
    int result;
};

static int dir_emit_impl(struct dir_context *ctx, const char *name, int namlen,
                         loff_t offset, u64 ino, unsigned int d_type)
{
    struct dir_context_impl *impl = container_of(ctx, struct dir_context_impl, ctx);
    struct linux_dirent64 *dirent = impl->dirent;
    
    // 填充目录项信息
    strncpy(dirent->d_name, name, namlen);
    dirent->d_name[namlen] = 0;
    dirent->d_ino = ino;
    dirent->d_type = d_type;
    
    impl->result = 1;
    return 0;
}

static void load_kpm_modules(void)
{
    struct path root_path;
    struct file *fp;
    struct dir_context_impl ctx = {
        .ctx.actor = dir_emit_impl,
        .ctx.pos = 0,
    };
    int ret;

    // 打开KPM模块目录
    ret = kern_path(KPM_MODULE_DIR, LOOKUP_FOLLOW, &root_path);
    if (ret) {
        pr_err("Failed to open KPM directory: %d\n", ret);
        return;
    }

    // 打开目录进行遍历
    fp = dentry_open(&root_path, O_RDONLY | O_DIRECTORY, current_cred());
    if (IS_ERR(fp)) {
        pr_err("Failed to open KPM directory file: %ld\n", PTR_ERR(fp));
        path_put(&root_path);
        return;
    }

    // 遍历目录中的所有.kpm文件
    while (true) {
        struct linux_dirent64 *dirent;
        int result = 0;

        dirent = kzalloc(sizeof(*dirent), GFP_KERNEL);
        if (!dirent)
            break;

        ctx.dirent = dirent;
        ctx.result = 0;
        
        ret = iterate_dir(fp, &ctx.ctx);
        if (ret < 0 || !ctx.result) {
            kfree(dirent);
            break;
        }

        // 检查是否是.kpm文件
        if (strstr(dirent->d_name, ".kpm")) {
            char module_path[256];
            snprintf(module_path, sizeof(module_path), "%s/%s", KPM_MODULE_DIR, dirent->d_name);
            
            // 使用 prctl 系统调用加载模块
            ret = syscall(__NR_prctl, 0x4B534C4B /* KERNEL_SU_OPTION */, SUKISU_KPM_LOAD, module_path, NULL, &result);
            if (ret == 0) {
                pr_info("Loaded KPM module: %s, result: %d\n", module_path, result);
            } else {
                pr_err("Failed to load KPM module: %s, ret: %d\n", module_path, ret);
            }
        }

        kfree(dirent);
        ctx.ctx.pos++;
    }

    filp_close(fp, NULL);
    path_put(&root_path);
}

// 在内核启动时调用
static int __init kpm_loader_init(void) 
{
    pr_info("KPM loader initialized\n");
    load_kpm_modules();
    return 0;
}

module_init(kpm_loader_init);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("ShirkNeko");
MODULE_DESCRIPTION("KernelSU KPM Module Loader");