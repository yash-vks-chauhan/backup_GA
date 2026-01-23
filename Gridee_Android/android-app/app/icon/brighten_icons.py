#!/usr/bin/env python3
from PIL import Image, ImageEnhance
import os

# Load the original image
input_path = "/Users/yashchauhan/Gridee/android-app/app/icon/image.png"
output_path = "/Users/yashchauhan/Gridee/android-app/app/icon/image_bright.png"

# Open the image
img = Image.open(input_path)

# Enhance brightness (1.5 means 50% brighter)
enhancer = ImageEnhance.Brightness(img)
bright_img = enhancer.enhance(1.5)

# Enhance contrast slightly to maintain clarity
contrast_enhancer = ImageEnhance.Contrast(bright_img)
final_img = contrast_enhancer.enhance(1.2)

# Save the brightened image
final_img.save(output_path)
print(f"Brightened image saved to: {output_path}")

# Define the icon sizes and directories
icon_sizes = [
    (48, "mipmap-mdpi"),
    (72, "mipmap-hdpi"), 
    (96, "mipmap-xhdpi"),
    (144, "mipmap-xxhdpi"),
    (192, "mipmap-xxxhdpi")
]

base_res_dir = "/Users/yashchauhan/Gridee/android-app/app/src/main/res"

# Create all icon sizes
for size, density_dir in icon_sizes:
    # Regular launcher icon
    resized_img = final_img.resize((size, size), Image.Resampling.LANCZOS)
    regular_path = os.path.join(base_res_dir, density_dir, "ic_launcher.png")
    resized_img.save(regular_path)
    print(f"Created {density_dir}/ic_launcher.png ({size}x{size})")
    
    # Round launcher icon (same image for now)
    round_path = os.path.join(base_res_dir, density_dir, "ic_launcher_round.png") 
    resized_img.save(round_path)
    print(f"Created {density_dir}/ic_launcher_round.png ({size}x{size})")

print("All brightened icons created successfully!")
