use anyhow::Result;
use notify::{Watcher, RecommendedWatcher, RecursiveMode, EventKind}; 
use std::path::Path;
use std::fs;

pub const KPM_DIR: &str = "/data/adb/kpm";
pub const KPMMGR_PATH: &str = "/data/adb/ksu/bin/kpmmgr";


pub fn ensure_kpm_dir() -> Result<()> {
    if !Path::new(KPM_DIR).exists() {
        fs::create_dir_all(KPM_DIR)?;
    }
    Ok(())
}

pub fn start_kpm_watcher() -> Result<()> {
    ensure_kpm_dir()?;

    // 检查是否处于安全模式
    if crate::utils::is_safe_mode() {
        log::warn!("The system is in safe mode and is deleting all KPM modules...");
        if let Err(e) = remove_all_kpms() {
            log::error!("Error deleting all KPM modules: {}", e);
        }
        return Ok(());
    }

    let mut watcher = notify::recommended_watcher(|res| {
        match res {
            Ok(event) => handle_kpm_event(event),
            Err(e) => log::error!("monitoring error: {:?}", e),
        }
    })?;

    watcher.watch(Path::new(KPM_DIR), RecursiveMode::NonRecursive)?;
    Ok(())
}

// 处理 KPM 文件事件
fn handle_kpm_event(event: notify::Event) {
    if event.kind.is_create() {
        event.paths.iter().for_each(|path| {
            if path.extension().map_or(false, |ext| ext == "kpm") {
                let _ = load_kpm(path);
            }
        });
    }

    if event.kind.is_remove() {
        event.paths.iter().for_each(|path| {
            if let Some(name) = path.file_stem() {
                let _ = unload_kpm(name.to_string_lossy().as_ref());
            }
        });
    }
}

// 加载 KPM 模块
pub fn load_kpm(path: &Path) -> Result<()> {
    let status = std::process::Command::new(KPMMGR_PATH)
        .args(["load", path.to_str().unwrap(), ""])
        .status()?;
    
    if status.success() {
        log::info!("Loaded KPM: {}", path.display());
    }
    Ok(())
}

// 卸载 KPM 模块并删除文件
pub fn unload_kpm(name: &str) -> Result<()> {
    let status = std::process::Command::new(KPMMGR_PATH)
        .args(["unload", name])
        .status()?;
    
    if status.success() {
        let kpm_path = Path::new(KPM_DIR).join(format!("{}.kpm", name));
        if kpm_path.exists() {
            if let Err(e) = fs::remove_file(&kpm_path) {
                log::error!("Failed to delete KPM file: {}", e);
            } else {
                log::info!("Deleted KPM files: {}", kpm_path.display());
            }
        }
        log::info!("Uninstalled KPM: {}", name);
    }
    Ok(())
}

// 删除所有 KPM 模块
pub fn remove_all_kpms() -> Result<()> {
    ensure_kpm_dir()?;
    
    for entry in fs::read_dir(KPM_DIR)? {
        let path = entry?.path();
        if path.extension().map_or(false, |ext| ext == "kpm") {
            if let Some(name) = path.file_stem() {
                let _ = unload_kpm(name.to_string_lossy().as_ref());
            }
        }
    }
    Ok(())
}

// 加载所有现有的 KPM 模块
pub fn load_existing_kpms() -> Result<()> {
    ensure_kpm_dir()?;
    
    for entry in fs::read_dir(KPM_DIR)? {
        let path = entry?.path();
        if path.extension().map_or(false, |ext| ext == "kpm") {
            let _ = load_kpm(&path);
        }
    }
    Ok(())
}