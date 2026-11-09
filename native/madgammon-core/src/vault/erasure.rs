use reed_solomon_erasure::galois_8::ReedSolomon;
use thiserror::Error;

#[derive(Error, Debug, PartialEq, Eq)]
pub enum ErasureError {
    #[error("Invalid shard parameters: data_shards={0}, parity_shards={1}")]
    InvalidParameters(usize, usize),
    #[error("Reed Solomon engine error")]
    EngineError,
    #[error("Not enough surviving shards to reconstruct: got {0}, need {1}")]
    InsufficientShards(usize, usize),
}

pub struct ShardedVault {
    data_shards: usize,
    parity_shards: usize,
}

impl ShardedVault {
    pub fn new(data_shards: usize, parity_shards: usize) -> Result<Self, ErasureError> {
        if data_shards == 0 || parity_shards == 0 || data_shards + parity_shards > 256 {
            return Err(ErasureError::InvalidParameters(data_shards, parity_shards));
        }
        Ok(Self {
            data_shards,
            parity_shards,
        })
    }

    /// Splits input payload into data shards and computes parity shards.
    pub fn encode(&self, payload: &[u8]) -> Result<Vec<Vec<u8>>, ErasureError> {
        let rs = ReedSolomon::new(self.data_shards, self.parity_shards)
            .map_err(|_| ErasureError::EngineError)?;

        // Shard size must be equal across all shards (round up to multiple of data_shards)
        let total_len = payload.len();
        let shard_size = (total_len + self.data_shards - 1) / self.data_shards;
        let padded_len = shard_size * self.data_shards;

        let mut data = Vec::with_capacity(padded_len);
        data.extend_from_slice(payload);
        data.resize(padded_len, 0u8);

        let mut shards: Vec<Vec<u8>> = (0..self.data_shards)
            .map(|i| data[i * shard_size..(i + 1) * shard_size].to_vec())
            .collect();

        for _ in 0..self.parity_shards {
            shards.push(vec![0u8; shard_size]);
        }

        rs.encode(&mut shards).map_err(|_| ErasureError::EngineError)?;
        Ok(shards)
    }

    /// Reconstructs original payload from a slice of optional shards.
    pub fn reconstruct(
        &self,
        shards: &mut [Option<Vec<u8>>],
        original_length: usize,
    ) -> Result<Vec<u8>, ErasureError> {
        let rs = ReedSolomon::new(self.data_shards, self.parity_shards)
            .map_err(|_| ErasureError::EngineError)?;

        let present_count = shards.iter().filter(|s| s.is_some()).count();
        if present_count < self.data_shards {
            return Err(ErasureError::InsufficientShards(
                present_count,
                self.data_shards,
            ));
        }

        rs.reconstruct(shards).map_err(|_| ErasureError::EngineError)?;

        let mut reconstructed = Vec::with_capacity(original_length);
        for shard in shards.iter().take(self.data_shards) {
            if let Some(ref data) = shard {
                reconstructed.extend_from_slice(data);
            }
        }
        reconstructed.truncate(original_length);
        Ok(reconstructed)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_reed_solomon_sharding_and_reconstruction() {
        let vault = ShardedVault::new(4, 2).unwrap();
        let original_data = b"MadGammon Secret Sovereign Seed Vault Payload 2026";

        let shards = vault.encode(original_data).unwrap();
        assert_eq!(shards.len(), 6);

        // Simulate losing 2 shards (e.g. shard 1 and shard 4)
        let mut surviving: Vec<Option<Vec<u8>>> = shards.into_iter().map(Some).collect();
        surviving[1] = None;
        surviving[4] = None;

        let recovered = vault.reconstruct(&mut surviving, original_data.len()).unwrap();
        assert_eq!(recovered, original_data);
    }
}
