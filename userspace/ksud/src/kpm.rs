use anyhow::Result;
use notify::{Watcher, RecommendedWatcher, RecursiveMode, EventKind};
use std::path::Path;

const KPM_DIR: &str = "/data/adb/kpm";

pub fn start_kpm_watcher() -> Result<()> {
    let mut watcher = notify::recommended_watcher(|res| {
        match res {
            Ok(event) => handle_kpm_event(event),
            Err(e) => log::error!("watch error: {:?}", e),
        }
    })?;

    watcher.watch(Path::new(KPM_DIR), RecursiveMode::NonRecursive)?;
    Ok(())
}

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

fn load_kpm(path: &Path) -> Result<()> {
    let status = std::process::Command::new("/data/adb/ksu/bin/kpmmgr")
        .args(["load", path.to_str().unwrap(), ""])
        .status()?;
    
    if status.success() {
        log::info!("Loaded KPM: {}", path.display());
    }
    Ok(())
}

fn unload_kpm(name: &str) -> Result<()> {
    let status = std::process::Command::new("/data/adb/ksu/bin/kpmmgr")
        .args(["unload", name])
        .status()?;
    
    if status.success() {
        log::info!("Unloaded KPM: {}", name);
    }
    Ok(())
}