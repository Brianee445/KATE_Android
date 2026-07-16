#!/usr/bin/env python3
"""
Kate Avatar Generator - One Source to Rule Them All
====================================================
Place your source avatar at: app/src/main/res/source/kate_avatar_source.png
Run this script: python scripts/generate_kate_avatars.py

It will generate:
- All density variants (MDPI → XXXHDPI)
- All states (idle, listening, thinking, speaking, sleeping, productive, error)
- Proper color overlays for each state
- App launcher icons
"""

import os
import sys
import shutil
from pathlib import Path
from PIL import Image, ImageFilter, ImageEnhance, ImageDraw, ImageChops

# ==================== CONFIGURATION ====================

# Avatar states with their colors and effects
AVATAR_STATES = {
    "idle": {
        "color": (124, 58, 237, 255),    # #7C3AED - Purple
        "effect": "glow",
        "glow_color": (124, 58, 237, 120),
        "description": "Default - waiting"
    },
    "listening": {
        "color": (212, 255, 79, 255),     # #D4FF4F - Green/Yellow
        "effect": "pulse",
        "glow_color": (212, 255, 79, 180),
        "description": "Listening - green glow"
    },
    "thinking": {
        "color": (124, 58, 237, 255),     # #7C3AED - Purple
        "effect": "pulse_fast",
        "glow_color": (124, 58, 237, 200),
        "description": "Processing - fast pulse"
    },
    "speaking": {
        "color": (255, 107, 157, 255),    # #FF6B9D - Pink
        "effect": "wave",
        "glow_color": (255, 107, 157, 180),
        "description": "Speaking - pink glow"
    },
    "sleeping": {
        "color": (167, 154, 184, 255),    # #A79AB8 - Muted
        "effect": "dim",
        "glow_color": (167, 154, 184, 60),
        "description": "Sleeping - dimmed"
    },
    "productive": {
        "color": (76, 175, 80, 255),      # #4CAF50 - Green
        "effect": "glow",
        "glow_color": (76, 175, 80, 160),
        "description": "Productivity mode"
    },
    "error": {
        "color": (255, 84, 112, 255),     # #FF5470 - Red
        "effect": "shake",
        "glow_color": (255, 84, 112, 180),
        "description": "Error state"
    }
}

# Density configurations (DPI buckets)
DENSITIES = {
    "drawable-xxxhdpi": 192,   # 4.0x - Extra Extra Extra High
    "drawable-xxhdpi": 144,    # 3.0x - Extra Extra High (BASE)
    "drawable-xhdpi": 96,      # 2.0x - Extra High
    "drawable-hdpi": 72,       # 1.5x - High
    "drawable-mdpi": 48,       # 1.0x - Medium
    "drawable-ldpi": 36,       # 0.75x - Low (optional)
}

# App icon sizes
ICON_SIZES = {
    "mipmap-xxxhdpi": 192,
    "mipmap-xxhdpi": 144,
    "mipmap-xhdpi": 96,
    "mipmap-hdpi": 72,
    "mipmap-mdpi": 48,
}

# ==================== IMAGE PROCESSING FUNCTIONS ====================

def apply_color_overlay(image, color):
    """Apply a semi-transparent color overlay"""
    if image.mode != 'RGBA':
        image = image.convert('RGBA')
    
    overlay = Image.new('RGBA', image.size, color)
    # Blend with 35% opacity for the overlay
    return Image.blend(image, overlay, 0.35)

def apply_glow(image, glow_color, radius=12, intensity=1.4):
    """Apply a glow effect around the avatar"""
    if image.mode != 'RGBA':
        image = image.convert('RGBA')
    
    # Create glow layer from alpha channel
    alpha = image.split()[-1]
    glow = Image.new('RGBA', image.size, (0, 0, 0, 0))
    glow.putalpha(alpha)
    
    # Color the glow
    glow = apply_color_overlay(glow, glow_color)
    
    # Blur for glow effect
    glow = glow.filter(ImageFilter.GaussianBlur(radius=radius))
    
    # Enhance brightness
    enhancer = ImageEnhance.Brightness(glow)
    glow = enhancer.enhance(intensity)
    
    # Composite glow with original
    result = Image.alpha_composite(glow, image)
    return result

def apply_dim_effect(image, brightness=0.35):
    """Dim the image for sleeping state"""
    enhancer = ImageEnhance.Brightness(image)
    return enhancer.enhance(brightness)

def apply_shake_effect(image):
    """Add subtle red distortion for error state"""
    # Just add a red glow overlay for simplicity
    return apply_glow(image, (255, 50, 50, 150), radius=15, intensity=1.6)

def add_pulse_ring(image, color):
    """Add a subtle pulse ring around the avatar"""
    result = image.copy()
    draw = ImageDraw.Draw(result)
    size = result.size[0]
    
    # Draw inner glow ring
    ring_size = size - 8
    draw.ellipse(
        [4, 4, ring_size, ring_size],
        outline=(color[0], color[1], color[2], 80),
        width=3
    )
    
    # Draw outer ring
    ring_size = size - 14
    draw.ellipse(
        [7, 7, ring_size, ring_size],
        outline=(color[0], color[1], color[2], 40),
        width=2
    )
    
    return result

def generate_avatar_state(source_img, state, config, output_dir):
    """Generate all variants for a single avatar state"""
    try:
        # Start with source
        img = source_img.copy()
        
        # Apply state-specific effects
        effect = config["effect"]
        color = config["color"]
        glow_color = config["glow_color"]
        
        if effect == "glow":
            img = apply_glow(img, glow_color)
        elif effect == "pulse" or effect == "pulse_fast":
            img = apply_glow(img, glow_color, radius=14, intensity=1.2)
            img = add_pulse_ring(img, color)
        elif effect == "wave":
            img = apply_glow(img, glow_color, radius=18, intensity=1.6)
        elif effect == "dim":
            img = apply_dim_effect(img, 0.35)
        elif effect == "shake":
            img = apply_shake_effect(img)
        
        # Apply color overlay (subtle tint)
        img = apply_color_overlay(img, color)
        
        # Generate all densities
        for density_name, size in DENSITIES.items():
            resized = img.resize((size, size), Image.Resampling.LANCZOS)
            
            # Create directory
            density_dir = Path(output_dir) / density_name
            density_dir.mkdir(parents=True, exist_ok=True)
            
            # Save
            filename = f"kate_avatar_{state}.png"
            filepath = density_dir / filename
            resized.save(filepath, "PNG", optimize=True)
            
        return True
        
    except Exception as e:
        print(f"  ❌ Failed: {e}")
        return False

def generate_app_icons(source_img, output_dir):
    """Generate app launcher icons"""
    try:
        for density_name, size in ICON_SIZES.items():
            resized = source_img.resize((size, size), Image.Resampling.LANCZOS)
            
            density_dir = Path(output_dir) / density_name
            density_dir.mkdir(parents=True, exist_ok=True)
            
            # Save both regular and round icons
            resized.save(density_dir / "ic_launcher.png", "PNG", optimize=True)
            resized.save(density_dir / "ic_launcher_round.png", "PNG", optimize=True)
            
        return True
    except Exception as e:
        print(f"  ❌ Failed to generate icons: {e}")
        return False

def generate_adaptive_icon(output_dir):
    """Generate adaptive icon XML (Android 8+)"""
    # Create the adaptive icon directory
    adaptive_dir = Path(output_dir) / "mipmap-anydpi-v26"
    adaptive_dir.mkdir(parents=True, exist_ok=True)
    
    # Foreground vector
    foreground_xml = '''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/ic_launcher_foreground" />
</layer-list>'''
    
    with open(adaptive_dir / "ic_launcher.xml", "w") as f:
        f.write(foreground_xml)
    with open(adaptive_dir / "ic_launcher_round.xml", "w") as f:
        f.write(foreground_xml)
    
    # Also create a simple foreground vector
    drawable_dir = Path(output_dir) / "drawable"
    drawable_dir.mkdir(parents=True, exist_ok=True)
    
    # Simple vector shape for the foreground
    vector_xml = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#7C3AED"
        android:pathData="M54,24 C37,24 24,37 24,54 C24,71 37,84 54,84 C71,84 84,71 84,54 C84,37 71,24 54,24Z"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="2" />
</vector>'''
    
    with open(drawable_dir / "ic_launcher_foreground.xml", "w") as f:
        f.write(vector_xml)
    
    # Background (purple)
    background_xml = '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#7C3AED" />
</shape>'''
    
    with open(drawable_dir / "ic_launcher_background.xml", "w") as f:
        f.write(background_xml)

# ==================== MAIN ====================

def main():
    print("=" * 55)
    print("  🎨 KATE AVATAR GENERATOR - One Source to Rule Them All")
    print("=" * 55)
    
    # Find project root
    script_dir = Path(__file__).parent.absolute()
    project_root = script_dir.parent
    source_dir = project_root / "app" / "src" / "main" / "res" / "source"
    output_dir = project_root / "app" / "src" / "main" / "res"
    
    # Source avatar path
    source_path = source_dir / "kate_avatar_source.png"
    
    # Check source exists
    if not source_path.exists():
        print(f"\n❌ Source file not found!")
        print(f"   Expected: {source_path}")
        print(f"\n📝 Please create your avatar (144x144px PNG) at:")
        print(f"   {source_dir}")
        print(f"   Name it: kate_avatar_source.png")
        sys.exit(1)
    
    print(f"\n📁 Source: {source_path}")
    print(f"📁 Output: {output_dir}\n")
    
    # Load source image
    try:
        source_img = Image.open(source_path).convert('RGBA')
        print(f"✅ Loaded: {source_img.size[0]}x{source_img.size[1]}px")
    except Exception as e:
        print(f"❌ Failed to load source: {e}")
        sys.exit(1)
    
    # ============ GENERATE AVATARS ============
    print("\n🔄 Generating avatar states...\n")
    
    success_count = 0
    for state, config in AVATAR_STATES.items():
        print(f"  → {state} ({config['description']})")
        if generate_avatar_state(source_img, state, config, output_dir):
            success_count += 1
            print(f"    ✅ Generated all densities")
    
    print(f"\n✅ Generated {success_count}/{len(AVATAR_STATES)} avatar states")
    
    # ============ GENERATE APP ICONS ============
    print("\n🎯 Generating app launcher icons...")
    
    if generate_app_icons(source_img, output_dir):
        print("  ✅ App icons generated")
    
    # ============ GENERATE ADAPTIVE ICON ============
    print("\n📱 Generating adaptive icon (Android 8+)...")
    
    generate_adaptive_icon(output_dir)
    print("  ✅ Adaptive icon generated")
    
    # ============ SUMMARY ============
    print("\n" + "=" * 55)
    print("✅ ALL DONE! Avatars are ready.")
    print("=" * 55)
    print("\n📁 Generated files:")
    
    # List generated directories
    for density in DENSITIES.keys():
        density_path = output_dir / density
        if density_path.exists():
            count = len(list(density_path.glob("kate_avatar_*.png")))
            print(f"   📂 {density}: {count} avatar files")
    
    print(f"\n📂 mipmap-*/: App icons")
    print(f"📂 mipmap-anydpi-v26/: Adaptive icons")
    print(f"📂 drawable/: Vector assets")
    
    print("\n" + "=" * 55)
    print("  🚀 Next: Build and test!")
    print("  ./gradlew assembleDebug")
    print("=" * 55)

if __name__ == "__main__":
    main()
