import os

def leer_y_formatear(ruta_absoluta, proyecto_root):
    """
    Lee un archivo y devuelve su contenido formateado con un encabezado
    que incluye la ruta relativa.
    """
    try:
        with open(ruta_absoluta, 'r', encoding='utf-8') as f:
            contenido = f.read()
        
        # Obtener la ruta relativa y usar separadores /
        ruta_relativa = os.path.relpath(ruta_absoluta, proyecto_root).replace('\\', '/')
        
        print(f'Procesando: {ruta_relativa}')
        
        # Devolver el contenido con un encabezado claro
        return f"--- INICIO: {ruta_relativa} ---\n\n{contenido}\n\n--- FIN: {ruta_relativa} ---"
    
    except FileNotFoundError:
        ruta_relativa = os.path.relpath(ruta_absoluta, proyecto_root).replace('\\', '/')
        print(f'ADVERTENCIA: No se encontró el archivo: {ruta_relativa}')
        return f"--- ARCHIVO NO ENCONTRADO: {ruta_relativa} ---"
    
    except Exception as e:
        ruta_relativa = os.path.relpath(ruta_absoluta, proyecto_root).replace('\\', '/')
        print(f'ERROR al procesar {ruta_relativa}: {e}')
        return f"--- ERROR AL LEER: {ruta_relativa} ---"

def main():
    # El script asume que está en la carpeta raíz del proyecto
    PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
    OUTPUT_FILE = os.path.join(PROJECT_ROOT, 'concatenado_codigo.txt')
    
    partes = []

    # --- 1. Archivo AndroidManifest.xml ---
    manifest_path = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'AndroidManifest.xml')
    partes.append(leer_y_formatear(manifest_path, PROJECT_ROOT))

    # --- 2. Todos los archivos .kt en la ruta de tu paquete ---
    # (basado en com/example/safebankid)
    kt_root = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'java', 'com', 'example', 'safebankid')
    
    for root, dirs, files in os.walk(kt_root):
        for nombre in files:
            if nombre.endswith('.kt'):
                ruta_archivo = os.path.join(root, nombre)
                partes.append(leer_y_formatear(ruta_archivo, PROJECT_ROOT))

    # --- 3. Archivos de Gradle (de la imagen) ---
    # Lista de rutas relativas de los archivos de Gradle
    gradle_files = [
        'build.gradle.kts',                           # Proyecto
        'app/build.gradle.kts',                       # Módulo :app
        'app/proguard-rules.pro',
        'gradle.properties',
        'gradle/wrapper/gradle-wrapper.properties',   # Ruta estándar
        'gradle/libs.versions.toml',                  # <-- RUTA CORREGIDA
        'settings.gradle.kts'
    ]
    
    for ruta_relativa in gradle_files:
        ruta_abs = os.path.join(PROJECT_ROOT, ruta_relativa)
        partes.append(leer_y_formatear(ruta_abs, PROJECT_ROOT))

    # --- Concatenar y escribir ---
    resultado = '\n\n'.join(partes)
    
    try:
        with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
            f.write(resultado)
        print(f'\n¡Éxito! Concatenación completa.')
        print(f'Archivo generado en: {OUTPUT_FILE}')
    except Exception as e:
        print(f'\nERROR al escribir el archivo de salida: {e}')

if __name__ == '__main__':
    main()