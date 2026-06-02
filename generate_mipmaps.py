from PIL import Image
import os

source_path = "assets/focuslock_icon.png"
res_dir = "app/src/main/res"
sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

if not os.path.exists(source_path):
    print(f"Source image {source_path} not found.")
    exit(1)

img = Image.open(source_path)

for dpi, size in sizes.items():
    out_dir = os.path.join(res_dir, dpi)
    os.makedirs(out_dir, exist_ok=True)
    
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    
    out_path = os.path.join(out_dir, "ic_launcher.png")
    resized.save(out_path)
    
    # Also save ic_launcher_round.png
    out_path_round = os.path.join(out_dir, "ic_launcher_round.png")
    resized.save(out_path_round)

print("Mipmaps generated successfully.")
