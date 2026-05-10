use anyhow::Context;
use std::fs;
use std::path::Path;

pub fn get_device_id() -> anyhow::Result<String> {
    match get_machine_id() {
        Ok(id) if !id.is_empty() => return Ok(id),
        _ => {
            log::warn!("Failed to get system ID. Using fallback.");
        }
    }
    get_fallback_id()
}

#[cfg(target_os = "windows")]
fn get_machine_id() -> anyhow::Result<String> {
    use winapi::shared::minwindef::{DWORD, HKEY};
    use winapi::um::winnt::{KEY_READ, REG_SZ};
    use winapi::um::winreg::{RegCloseKey, RegOpenKeyExW, RegQueryValueExW, HKEY_LOCAL_MACHINE};
    use widestring::U16CString;
    use std::ptr;

    unsafe {
        let sub_key = U16CString::from_str("SOFTWARE\\Microsoft\\Cryptography")?;
        let value_name = U16CString::from_str("MachineGuid")?;
        let mut hkey: HKEY = ptr::null_mut();
        if RegOpenKeyExW(HKEY_LOCAL_MACHINE, sub_key.as_ptr(), 0, KEY_READ, &mut hkey) != 0 {
            anyhow::bail!("RegOpenKeyExW failed");
        }
        let mut buf = vec![0u16; 256];
        let mut buf_len: DWORD = (buf.len() * 2) as DWORD;
        let mut reg_type: DWORD = 0;
        let ret = RegQueryValueExW(
            hkey,
            value_name.as_ptr(),
            ptr::null_mut(),
            &mut reg_type,
            buf.as_mut_ptr() as *mut u8,
            &mut buf_len,
        );
        RegCloseKey(hkey);
        if ret != 0 || reg_type != REG_SZ {
            anyhow::bail!("RegQueryValueExW failed");
        }
        let len = (buf_len as usize / 2).saturating_sub(1);
        Ok(String::from_utf16_lossy(&buf[..len]))
    }
}

#[cfg(target_os = "macos")]
fn get_machine_id() -> anyhow::Result<String> {
    let output = std::process::Command::new("ioreg")
        .args(["-rd1", "-c", "IOPlatformExpertDevice"])
        .output()?;
    let stdout = String::from_utf8_lossy(&output.stdout);
    for line in stdout.lines() {
        if line.contains("IOPlatformUUID") {
            // line looks like:   "IOPlatformUUID" = "XXXXXXXX-..."
            let parts: Vec<&str> = line.splitn(2, '=').collect();
            if parts.len() == 2 {
                let val = parts[1].trim().trim_matches('"');
                if !val.is_empty() {
                    return Ok(val.to_string());
                }
            }
        }
    }
    anyhow::bail!("IOPlatformUUID not found")
}

#[cfg(target_os = "linux")]
fn get_machine_id() -> anyhow::Result<String> {
    for path in &["/etc/machine-id", "/var/lib/dbus/machine-id"] {
        if let Ok(id) = fs::read_to_string(path) {
            let id = id.trim().to_string();
            if !id.is_empty() {
                return Ok(id);
            }
        }
    }
    anyhow::bail!("machine-id not found")
}

#[cfg(target_os = "android")]
fn get_machine_id() -> anyhow::Result<String> {
    anyhow::bail!("not supported on android")
}

#[cfg(target_os = "ios")]
fn get_machine_id() -> anyhow::Result<String> {
    anyhow::bail!("not supported on ios")
}

#[cfg(not(any(
    target_os = "windows",
    target_os = "macos",
    target_os = "linux",
    target_os = "android",
    target_os = "ios"
)))]
fn get_machine_id() -> anyhow::Result<String> {
    anyhow::bail!("unsupported platform")
}

fn get_fallback_id() -> anyhow::Result<String> {
    let path = Path::new("device_id");

    if let Ok(content) = fs::read_to_string(path) {
        let id = content.trim();
        if !id.is_empty() {
            return Ok(id.to_string());
        }
    }

    let new_id = uuid::Uuid::new_v4().to_string();
    fs::write(path, &new_id).context("Failed to write device_id file")?;
    Ok(new_id)
}
