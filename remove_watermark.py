import json
import os

# Directory containing Lottie files
assets_dir = '/Users/yashchauhan/gridee-android/Gridee_Android/android-app/app/src/main/assets'

# Get all JSON files
json_files = [f for f in os.listdir(assets_dir) if f.endswith('.json')]

print(f"Found {len(json_files)} Lottie files\n")

for filename in json_files:
    file_path = os.path.join(assets_dir, filename)
    try:
        # Read the Lottie file
        with open(file_path, 'r') as f:
            data = json.load(f)
        
        # Check if it has layers
        if 'layers' not in data:
            print(f"- {filename}: Not a Lottie animation (no layers)")
            continue
            
        # Remove watermark layer (ind: 12345679)
        original_count = len(data['layers'])
        data['layers'] = [layer for layer in data['layers'] if layer.get('ind') != 12345679]
        removed = original_count - len(data['layers'])
        
        # Write back only if watermark was found
        if removed > 0:
            with open(file_path, 'w') as f:
                json.dump(data, f)
            print(f"✓ {filename}: Watermark removed!")
        else:
            print(f"- {filename}: No watermark found")
    except Exception as e:
        print(f"✗ {filename}: Error - {e}")

print("\n✅ All files processed successfully!")

