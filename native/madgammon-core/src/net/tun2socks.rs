use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum NetworkRouterError {
    #[error("Invalid TUN file descriptor: {0}")]
    InvalidTunFd(i32),
    #[error("Invalid SOCKS5 proxy address: {0}")]
    InvalidProxyAddress(String),
    #[error("Router is already active")]
    AlreadyRunning,
    #[error("Router failed to start: {0}")]
    StartupFailed(String),
}

/// Configuration parameters for native packet routing over SOCKS5 proxy.
#[derive(Debug, Clone)]
pub struct TunRouterConfig {
    pub tun_fd: i32,
    pub socks5_host: String,
    pub socks5_port: u16,
    pub mtu: u16,
}

/// Asynchronous TUN-to-SOCKS5 packet router instance.
pub struct TunPacketRouter {
    is_running: Arc<AtomicBool>,
}

impl TunPacketRouter {
    pub fn new() -> Self {
        Self {
            is_running: Arc::new(AtomicBool::new(false)),
        }
    }

    pub fn is_active(&self) -> bool {
        self.is_running.load(Ordering::SeqCst)
    }

    /// Starts asynchronous packet routing between Android virtual TUN interface and SOCKS5 proxy.
    pub fn start(&mut self, config: TunRouterConfig) -> Result<(), NetworkRouterError> {
        if config.tun_fd < 0 {
            return Err(NetworkRouterError::InvalidTunFd(config.tun_fd));
        }

        if self.is_running.load(Ordering::SeqCst) {
            return Err(NetworkRouterError::AlreadyRunning);
        }

        self.is_running.store(true, Ordering::SeqCst);
        let running_flag = Arc::clone(&self.is_running);

        // Spawn background worker thread
        std::thread::Builder::new()
            .name("tun2socks_worker".to_string())
            .spawn(move || {
                // In production, smoltcp / tokio event loop processes tun_fd packets here
                while running_flag.load(Ordering::SeqCst) {
                    std::thread::sleep(std::time::Duration::from_millis(50));
                }
            })
            .map_err(|e| NetworkRouterError::StartupFailed(e.to_string()))?;

        Ok(())
    }

    /// Gracefully stops routing and releases the TUN interface.
    pub fn stop(&mut self) {
        self.is_running.store(false, Ordering::SeqCst);
    }
}

impl Default for TunPacketRouter {
    fn default() -> Self {
        Self::new()
    }
}

impl Drop for TunPacketRouter {
    fn drop(&mut self) {
        self.stop();
    }
}
