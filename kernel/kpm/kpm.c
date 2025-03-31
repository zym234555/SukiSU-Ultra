/* SPDX-License-Identifier: GPL-2.0-or-later */
/* 
 * Copyright (C) 2025 Liankong (xhsw.new@outlook.com). All Rights Reserved.
 * 本代码由GPL-2授权
 * 
 * 适配KernelSU的KPM 内核模块加载器兼容实现
 * 
 * 集成了 ELF 解析、内存布局、符号处理、重定位（支持 ARM64 重定位类型）
 * 并参照KernelPatch的标准KPM格式实现加载和控制
 */
#include <linux/export.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/fs.h>
#include <linux/kernfs.h>
#include <linux/file.h>
#include <linux/slab.h>
#include <linux/vmalloc.h>
#include <linux/uaccess.h>
#include <linux/elf.h>
#include <linux/kallsyms.h>
#include <linux/version.h>
#include <linux/list.h>
#include <linux/spinlock.h>
#include <linux/rcupdate.h>
#include <asm/elf.h>    /* 包含 ARM64 重定位类型定义 */
#include <linux/vmalloc.h>
#include <linux/mm.h>
#include <linux/string.h>
#include <asm/cacheflush.h>
#include <linux/module.h>
#include <linux/vmalloc.h>
#include <linux/set_memory.h>
#include <linux/version.h>
#include <linux/export.h>
#include <linux/slab.h>
#include <asm/insn.h>
#include <linux/kprobes.h>
#include <linux/stacktrace.h>
#include <linux/kallsyms.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5,0,0) && defined(CONFIG_MODULES)
#include <linux/moduleloader.h> // 需要启用 CONFIG_MODULES
#endif
#include "kpm.h"
#include "compact.h"

// ============================================================================================

int sukisu_kpm_load_module_path(const char* path, const char* args, void* ptr) {
    // This is a KPM module stub.
    return -1;
}

int sukisu_kpm_unload_module(const char* name, void* ptr) {
    // This is a KPM module stub.
    return -1;
}

int sukisu_kpm_num(void) {
    // This is a KPM module stub.
    return 0;
}

int sukisu_kpm_info(const char* name, void __user* out) {
    // This is a KPM module stub.
    return -1;
}

int sukisu_kpm_list(void __user* out, unsigned int bufferSize) {
    // This is a KPM module stub.
    return -1;
}

int sukisu_kpm_control(void __user* name, void __user* args) {
    return -1;
}

int sukisu_kpm_version(void __user* out, unsigned int bufferSize) {
    return -1;
}

EXPORT_SYMBOL(sukisu_kpm_load_module_path);
EXPORT_SYMBOL(sukisu_kpm_unload_module);
EXPORT_SYMBOL(sukisu_kpm_num);
EXPORT_SYMBOL(sukisu_kpm_info);
EXPORT_SYMBOL(sukisu_kpm_list);
EXPORT_SYMBOL(sukisu_kpm_version);
EXPORT_SYMBOL(sukisu_kpm_control);


int sukisu_handle_kpm(unsigned long arg3, unsigned long arg4, unsigned long arg5)
{
    if(arg3 == SUKISU_KPM_LOAD) {
        char kernel_load_path[256] = { 0 };
        char kernel_args_buffer[256] = { 0 };

        if(arg4 == 0) {
            return -1;
        }
        
        strncpy_from_user((char*)&kernel_load_path, (const char __user *)arg4, 255);
        if(arg5 != 0) {
            strncpy_from_user((char*)&kernel_args_buffer, (const char __user *)arg4, 255);
        }
        return sukisu_kpm_load_module_path((const char*)&kernel_load_path, (const char*) &kernel_args_buffer, NULL);
    } else if(arg3 == SUKISU_KPM_UNLOAD) {
        char kernel_name_buffer[256] = { 0 };

        if(arg4 == 0) {
            return -1;
        }
        
        strncpy_from_user((char*)&kernel_name_buffer, (const char __user *)arg4, 255);
        return sukisu_kpm_unload_module((const char*) &kernel_name_buffer, NULL);
    } else if(arg3 == SUKISU_KPM_NUM) {
        return sukisu_kpm_num();
    } else if(arg3 == SUKISU_KPM_INFO) {
        char kernel_name_buffer[256] = { 0 };

        if(arg4 == 0 || arg5 == 0) {
            return -1;
        }
        
        strncpy_from_user((char*)&kernel_name_buffer, (const char __user *)arg4, 255);
        return sukisu_kpm_info((const char*) &kernel_name_buffer, (char __user*) arg5);
    } else if(arg3 == SUKISU_KPM_LIST) {
        return sukisu_kpm_list((char __user*) arg4, (unsigned int) arg5);
    } else if(arg3 == SUKISU_KPM_VERSION) {
        return sukisu_kpm_version((char __user*) arg4, (unsigned int) arg5);
    }
    return 0;
}

int sukisu_is_kpm_control_code(unsigned long arg2) {
    return (arg2 >= CMD_KPM_CONTROL && arg2 <= CMD_KPM_CONTROL_MAX) ? 1 : 0;
}

EXPORT_SYMBOL(sukisu_handle_kpm);