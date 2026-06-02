from PIL import Image
import os

source_path = "assets/focuslock_icon.png"
if not os.path.exists(source_path):
    print(f"Source image not found: {source_path}")
    exit(1)

sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

img = Image.open(source_path).convert("RGBA")

for density, size in sizes.items():
    dir_path = f"app/src/main/res/mipmap-{density}"
    os.makedirs(dir_path, exist_ok=True)
    
    resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
    
    # Save standard icon
    resized_img.save(f"{dir_path}/ic_launcher.png")
    
    # For round icon, just save the same image if it's already round, 
    # or apply a mask to make it round if needed.
    # Assuming it's already suitable as a round icon or we can just copy it.
    resized_img.save(f"{dir_path}/ic_launcher_round.png")

print("Icons generated successfully.")
