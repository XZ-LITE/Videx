# 🌐 Инструкция по развертыванию сайта Videx на GitHub Pages

Все файлы готового сайта-визитки Videx (`index.html`, `style.css`, `script.js`) находятся в этой папке `website/`.

---

### 🚀 Способ A: Отдельный репозиторий `xz-lite.github.io` (Самый красивый домен)

1. Создай новый публичный репозиторий на GitHub и назови его **`xz-lite.github.io`** (или `videx-website`).
2. Скопируй содержимое папки `website/` (`index.html`, `style.css`, `script.js`) в этот новый репозиторий.
3. Залей файлы на GitHub (`git add .`, `git commit -m "Init site"`, `git push origin main`).
4. **Готово!** Через 1 минуту твой сайт автоматический появится в интернете по адресу:
   👉 **`https://xz-lite.github.io/`**

---

### 🚀 Способ B: Папка `/docs` в основном репозитории `Videx`

Если хочешь держать сайт в основном репозитории:
1. Скопируй содержимое папки `website/` в папку `docs/` основного репозитория Videx.
2. Открой GitHub ➔ репозиторий Videx ➔ **Settings ➔ Pages**.
3. Выбери **Build and deployment**:
   * **Source**: `Deploy from a branch`
   * **Branch**: `main` / Folder: `/docs`
   * Нажми **Save**.
4. Сайт будет доступен по адресу:
   👉 **`https://xz-lite.github.io/Videx/`**
