(function () {
    'use strict';

    const API_PROCESS = '/api/v1/custom/process';

    const MODEL_SELECTOR_ID = 'modelSelector';
    const MODEL_BADGE_ID = 'modelBadgeText';
    const MODEL_KEY = 'agentplatform:model';

    const form = document.getElementById('customForm');
    const taskTypeSelect = document.getElementById('taskTypeSelect');
    const promptInput = document.getElementById('promptInput');
    const processBtn = document.getElementById('processBtn');
    const processBtnLabel = document.getElementById('processBtnLabel');
    const clearBtn = document.getElementById('clearBtn');

    const emptyState = document.getElementById('customEmpty');
    const processingState = document.getElementById('customProcessing');
    const errorState = document.getElementById('customError');
    const errorText = document.getElementById('customErrorText');
    const resultState = document.getElementById('customResult');
    const taskBadge = document.getElementById('customTaskBadge');
    const timingEl = document.getElementById('customTiming');
    const resultBody = document.getElementById('customResultBody');

    const toastContainer = document.getElementById('toastContainer');

    // ─── Model Selector ─────────────────────────────────────────────────────────
    // Persists the active model (llama3 / qwen2.5) so the choice follows the user
    // across Custom → Chat page navigations (both pages host the selector).
    function selectedModel() {
        const selector = document.getElementById(MODEL_SELECTOR_ID);
        return selector ? selector.value : null;
    }

    function initModelSelector() {
        const selector = document.getElementById(MODEL_SELECTOR_ID);
        if (!selector) return;

        const saved = localStorage.getItem(MODEL_KEY);
        if (saved && Array.from(selector.options).some((option) => option.value === saved)) {
            selector.value = saved;
        }

        const syncBadge = () => {
            const badge = document.getElementById(MODEL_BADGE_ID);
            if (badge) badge.textContent = selector.value;
        };

        syncBadge();
        selector.addEventListener('change', () => {
            localStorage.setItem(MODEL_KEY, selector.value);
            syncBadge();
        });
    }

    function escapeHtml(text) {
        if (text === null || text === undefined) return '';
        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function showToast(message, type = 'info', duration = 5000) {
        if (!toastContainer) return;
        const toast = document.createElement('div');
        toast.className = `toast ${type === 'error' ? 'toast-error' : ''}`;
        const icon = type === 'error'
            ? '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>'
            : '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"></polyline></svg>';
        toast.innerHTML = `${icon}<span>${escapeHtml(message)}</span>`;
        toastContainer.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(30px)';
            toast.style.transition = 'all 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, duration);
    }

    function setBusy(busy) {
        if (busy) {
            processBtn.disabled = true;
            processBtnLabel.textContent = 'Processing…';
            taskTypeSelect.disabled = true;
            promptInput.disabled = true;
        } else {
            processBtn.disabled = !promptInput.value.trim();
            processBtnLabel.textContent = 'Run Task';
            taskTypeSelect.disabled = false;
            promptInput.disabled = false;
        }
    }

    function showView(view) {
        emptyState.hidden = view !== 'empty';
        processingState.hidden = view !== 'processing';
        errorState.hidden = view !== 'error';
        resultState.hidden = view !== 'result';
    }

    function showError(message) {
        errorText.textContent = message;
        // Keep the last result hidden; the error is the current state.
        showView('error');
    }

    function clearResult() {
        resultBody.innerHTML = '';
        resultState.hidden = true;
        showView('empty');
    }

    function renderResult(data) {
        const result = data && typeof data.result === 'object' ? data.result : {};
        taskBadge.textContent = data.taskType || 'GENERAL';
        timingEl.textContent = `Completed in ${data.executionTimeMs} ms`;

        const body = document.createDocumentFragment();

        const fields = [
            { key: 'summary', label: 'Summary' },
            { key: 'category', label: 'Category' },
            { key: 'confidence', label: 'Confidence' }
        ];

        for (const field of fields) {
            const value = result[field.key];
            if (value === null || value === undefined || value === '') continue;
            const block = document.createElement('div');
            block.className = 'custom-result-block';
            const label = document.createElement('span');
            label.className = 'custom-result-label';
            label.textContent = field.label;
            const content = document.createElement('p');
            content.className = 'custom-result-text';
            content.textContent = field.key === 'confidence'
                ? (typeof value === 'number' ? `${(value * 100).toFixed(0)}%` : value)
                : value;
            block.appendChild(label);
            block.appendChild(content);
            body.appendChild(block);
        }

        if (Array.isArray(result.keyPoints) && result.keyPoints.length > 0) {
            const block = document.createElement('div');
            block.className = 'custom-result-block';
            const label = document.createElement('span');
            label.className = 'custom-result-label';
            label.textContent = 'Key Points';
            const list = document.createElement('ul');
            list.className = 'custom-keypoints-list';
            result.keyPoints.forEach((point) => {
                const li = document.createElement('li');
                li.textContent = point;
                list.appendChild(li);
            });
            block.appendChild(label);
            block.appendChild(list);
            body.appendChild(block);
        }

        if (body.childNodes.length === 0) {
            const block = document.createElement('div');
            block.className = 'custom-result-block';
            const content = document.createElement('p');
            content.className = 'custom-result-text';
            content.textContent = 'The model returned an empty structured result.';
            block.appendChild(content);
            body.appendChild(block);
        }

        resultBody.innerHTML = '';
        resultBody.appendChild(body);
        showView('result');
    }

    async function processTask() {
        const prompt = promptInput.value.trim();
        if (!prompt) {
            showToast('Please enter some text to process.', 'error');
            promptInput.focus();
            return;
        }

        const request = {
            prompt: prompt,
            taskType: taskTypeSelect.value
        };
        const model = selectedModel();
        if (model) {
            request.model = model;
        }

        setBusy(true);
        errorState.hidden = true;
        resultState.hidden = true;
        emptyState.hidden = true;
        processingState.hidden = false;

        try {
            const response = await fetch(API_PROCESS, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });

            if (!response.ok) {
                let message = `Request failed with status ${response.status}.`;
                try {
                    const problem = await response.json();
                    if (problem && problem.detail) {
                        message = problem.detail;
                    } else if (problem && problem.title) {
                        message = problem.title;
                    }
                } catch (e) {
                    // Ignore — fall back to generic message.
                }
                throw new Error(message);
            }

            const data = await response.json();
            renderResult(data);
            showToast('Task completed successfully.', 'info');
        } catch (err) {
            console.error('Custom AI task failed:', err);
            showView('error');
            errorText.textContent = err.message || 'Something went wrong while processing your task.';
            showToast(errorText.textContent, 'error');
        } finally {
            processingState.hidden = true;
            setBusy(false);
        }
    }

    function clearAll() {
        promptInput.value = '';
        taskTypeSelect.value = 'SUMMARIZE';
        clearResult();
        promptInput.focus();
    }

    promptInput.addEventListener('input', () => {
        processBtn.disabled = !promptInput.value.trim();
    });
    processBtn.addEventListener('click', processTask);
    clearBtn.addEventListener('click', clearAll);
    form.addEventListener('submit', (e) => e.preventDefault());
    initModelSelector();
}());