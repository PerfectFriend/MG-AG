use serde::{Deserialize, Serialize};
use thiserror::Error;

#[derive(Error, Debug, PartialEq, Eq)]
pub enum SimpleXProtocolError {
    #[error("Invalid SimpleX URI schema: expected smp:// or xftp://")]
    InvalidUriScheme,
    #[error("Malformed command frame: {0}")]
    MalformedFrame(String),
    #[error("Connection closed unexpectedly")]
    ConnectionClosed,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum SmpCommand {
    NewQueue { server_id: String },
    Send { queue_id: String, payload: Vec<u8> },
    Receive { queue_id: String },
    Ack { message_id: String },
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SmpMessageFrame {
    pub message_id: String,
    pub queue_id: String,
    pub payload: Vec<u8>,
    pub timestamp: u64,
}

/// SimpleX SMP / XFTP framing and wire protocol parser.
pub struct SimpleXWireCodec;

impl SimpleXWireCodec {
    /// Encodes an SMP command into a newline-delimited JSON frame for transmission over SOCKS5 TCP.
    pub fn encode_command(command: &SmpCommand) -> Result<Vec<u8>, SimpleXProtocolError> {
        let json_str = serde_json::to_string(command)
            .map_err(|e| SimpleXProtocolError::MalformedFrame(e.to_string()))?;
        let mut bytes = json_str.into_bytes();
        bytes.push(b'\n');
        Ok(bytes)
    }

    /// Parses incoming raw bytes into a SimpleX message frame.
    pub fn decode_message(bytes: &[u8]) -> Result<SmpMessageFrame, SimpleXProtocolError> {
        let clean = std::str::from_utf8(bytes)
            .map_err(|e| SimpleXProtocolError::MalformedFrame(e.to_string()))?
            .trim();
        serde_json::from_str(clean)
            .map_err(|e| SimpleXProtocolError::MalformedFrame(e.to_string()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encode_and_decode_smp_frame() {
        let cmd = SmpCommand::Send {
            queue_id: "q_test_12345".to_string(),
            payload: vec![1, 2, 3, 4, 5],
        };
        let encoded = SimpleXWireCodec::encode_command(&cmd).unwrap();
        assert!(encoded.ends_with(b"\n"));

        let frame = SmpMessageFrame {
            message_id: "msg_abc".to_string(),
            queue_id: "q_test_12345".to_string(),
            payload: vec![1, 2, 3, 4, 5],
            timestamp: 1700000000,
        };
        let frame_bytes = serde_json::to_vec(&frame).unwrap();
        let decoded = SimpleXWireCodec::decode_message(&frame_bytes).unwrap();
        assert_eq!(decoded.message_id, "msg_abc");
        assert_eq!(decoded.payload, vec![1, 2, 3, 4, 5]);
    }
}
