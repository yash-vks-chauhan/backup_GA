import json
import os

# Path to the extracted JSON
input_path = 'temp_lottie/animations/3ad71820-90a7-4ba6-9bf6-398aa0c27c47.json'
output_path = 'app/src/main/assets/hero_animation.json'

with open(input_path, 'r') as f:
    data = json.load(f)

# Filter out the white solid background layer
# Looking for layer with ty=1 (Solid) and sc="#ffffff"
if 'layers' in data:
    new_layers = []
    for layer in data['layers']:
        is_white_bg = False
        if layer.get('ty') == 1 and layer.get('sc', '').lower() == '#ffffff':
            is_white_bg = True
        
        if not is_white_bg:
            new_layers.append(layer)
    
    data['layers'] = new_layers

# Save the modified JSON
with open(output_path, 'w') as f:
    json.dump(data, f)

print(f"Processed JSON saved to {output_path}")
