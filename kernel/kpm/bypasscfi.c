#include <linux/kprobes.h>
#include <linux/module.h>
#include <linux/version.h>
#include <linux/kallsyms.h>

/* CFI 检查函数符号 */
#define CFI_CHECK_FUNC "__cfi_check"

/* Kprobe 实例 */
static struct kprobe cfi_kp;

bool kpm_is_allow_address(unsigned long addr);

/*--------------------- kprobe 处理逻辑 ---------------------*/
static int handler_pre(struct kprobe *p, struct pt_regs *regs)
{
    unsigned long target_addr;
    
    /* 从寄存器获取目标地址（架构相关） */
#if defined(__aarch64__)
    target_addr = regs->regs[1];    // ARM64: 第二个参数在 X1
#elif defined(__x86_64__)
    target_addr = regs->si;         // x86_64: 第二个参数在 RSI
#else
    #error "Unsupported architecture"
#endif

    /* 根据自定义规则放行 */
    if (kpm_is_allow_address(target_addr)) {
        printk(KERN_INFO "CFI bypass at 0x%lx\n", target_addr);
#if defined(__aarch64__)
        regs->regs[0] = 0;  // 修改返回值：0 表示校验通过
#elif defined(__x86_64__)
        regs->ax = 0;        // x86 返回值在 RAX
#endif
        return 0; // 跳过原始 CFI 检查
    }
    
    return 0; // 继续执行原始检查
}

/*--------------------- 模块初始化/卸载 ---------------------*/
int kpm_cfi_bypass_init(void)
{
    unsigned long cfi_check_addr;
    
    /* 动态查找 CFI 检查函数 */
    cfi_check_addr = kallsyms_lookup_name(CFI_CHECK_FUNC);
    if (!cfi_check_addr) {
        printk(KERN_ERR "CFI check function not found\n");
        return -ENOENT;
    }
    
    /* 初始化 kprobe */
    memset(&cfi_kp, 0, sizeof(cfi_kp));
    cfi_kp.addr = (kprobe_opcode_t *)cfi_check_addr;
    cfi_kp.pre_handler = handler_pre;
    
    /* 注册 kprobe */
    if (register_kprobe(&cfi_kp) < 0) {
        printk(KERN_ERR "Register kprobe failed\n");
        return -EINVAL;
    }
    
    printk(KERN_INFO "CFI bypass module loaded\n");
    return 0;
}

void kpm_cfi_bypass_exit(void)
{
    unregister_kprobe(&cfi_kp);
    printk(KERN_INFO "CFI bypass module unloaded\n");
}
