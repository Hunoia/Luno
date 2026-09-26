{
  description = "Luno Android development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config.allowUnfree = true;
        config.android_sdk.accept_license = true;
      };

      androidComposition = pkgs.androidenv.composeAndroidPackages {
        platformToolsVersion = "latest";
        buildToolsVersions = [ "37.0.0" ];
        includeEmulator = false;
        includeSystemImages = false;
        includeSources = false;
        platformVersions = [ "36" ];
        abiVersions = [ "arm64-v8a" ];
      };
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        buildInputs = with pkgs; [
          jdk17
          androidComposition.androidsdk
        ];

        JAVA_HOME = "${pkgs.jdk17.home}";
        ANDROID_HOME = "${androidComposition.androidsdk}/libexec/android-sdk";
        ANDROID_SDK_ROOT = "${androidComposition.androidsdk}/libexec/android-sdk";

        shellHook = ''
          echo "Luno Android dev environment"
          echo "JDK: $(java -version 2>&1 | head -1)"
          echo "Android SDK: $ANDROID_HOME"
        '';
      };
    };
}
