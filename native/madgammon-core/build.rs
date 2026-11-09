fn main() {
    uniffi::generate_scaffolding("./src/madgammon_core.udl").expect("Building UniFFI scaffolding failed");
}
