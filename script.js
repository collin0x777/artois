

document.addEventListener("DOMContentLoaded", _ => {

    const text_window = document.getElementById("text-window");
    const text_highlights = document.getElementById("text-highlights");
    const generate_button = document.getElementById("generate-button");
    const stop_button = document.getElementById("stop-button");
    const clear_button = document.getElementById("clear-button");
    const generation_slider = document.getElementById("generation-count-slider");
    const generation_textbox = document.getElementById("generation-count-textbox");

    var remaining_generations = 0;
    var contextJson = null;

    async function generate() {
        if (remaining_generations > 0) {
            kwargs = fetchKwargs();

            batch_size = Math.min(Number(kwargs["batch_size"]), remaining_generations)

            kwargs["batch_size"] = batch_size;
            kwargs["context"] = text_window.value;
            
            remaining_generations -= batch_size;

            console.log(kwargs);

            try {
                const response = await fetch('http://localhost:1366/', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(kwargs),
                });

                response.json().then(json => {
                    contextJson = structuredClone(json);
                    renderTextArea(json);
                });

                generate();
            } catch(err) {
                console.error(`Error: ${err}`);
            }
        }
    }

    function renderTextArea(json) {
        console.log("Rendering text area");

        let tokens = json['tokens'];
        let attention = json['attention'];

        text_window.value = renderContext(tokens);

        let attentionHighlights = calculateAttentionHighlights(attention, tokens.length)
        let highlightedTokens = highlightTokens(tokens, attentionHighlights);
        text_highlights.innerHTML = generateTextBackdrop(highlightedTokens);
    }

    function updateTextArea() {
        console.log("Updating text area");

        let json = structuredClone(contextJson);
        let tokens = json['tokens'];
        let attention = json['attention'];
        let tokensToHighlight = calculateUnchangedTokens(tokens, text_window.value, renderContext(tokens));
        let attentionHighlights = calculateAttentionHighlights(attention, tokensToHighlight);
        highlightTokens(tokens, attentionHighlights);
        text_highlights.innerHTML = generateTextBackdrop(tokens);
    }

    function renderContext(tokens) {
        let text = "";

        for (let token of tokens) {
            text += token['text'];
        }
        return text;
    }

    function calculateUnchangedTokens(tokens, current_context, new_context) {

        if (current_context === new_context) {
            return 9999;
        }

        let unchangedCharacters = 0

        for (let i = 0; i < current_context.length; i++) {
            if (current_context[i] === new_context[i]) {
                unchangedCharacters += 1;
            } else {
                break;
            }
        }

        let characterCount = 0;
        for (let i = 0; i < tokens.length; i++) {
            characterCount += tokens[i]['text'].length;
            if (characterCount > unchangedCharacters) {
                return i;
            }
        }

        return 9999;
    }

    function calculateAttentionHighlights(attention, tokensToHighlight) {
        let attentionSums = [0];

        for (let i = 1; i < tokensToHighlight; i++) {
            attentionSums.push(0);
            for (let j = 0; j < i; j++) {
                attentionSums[j] += attention[i][j];
            }
        }

        let attentionHighlights = [''];
        for (let i = 1; i < tokensToHighlight; i++) {
            let attentionAvg = attentionSums[i] / (tokensToHighlight - i);

            if (attentionAvg > 0.05) {
                let GB = Math.floor((1 - attentionAvg) * 255)
                attentionHighlights.push('rgb(255, '+GB+', '+GB+')');
            }
        }

        return attentionHighlights;
    }

    function highlightTokens(tokens, highlights) {
        tokens.forEach((token, i) => {
            if (i < highlights.length) {
                if (highlights[i] !== '') {
                    token['color'] = highlights[i];
                }
            }
        })

        return tokens;
    }

    function generateTextBackdrop(tokens) {
        let text = "";

        for (let token of tokens) {
            let token_text = token['text']
            let token_color = token['color']
            if (token_color != null) {
                token_text = '<mark style="background-color: ' + token_color + '">' + token_text + '</mark>'
            }
            text += token_text;
        }

        return text;
    }

    generate_button.addEventListener("click", async _ => {
        remaining_generations = generation_textbox.value;
        generate()
    });

    function fetchKwargs() {
        keys = document.getElementsByClassName("kwargs-key")
        values = document.getElementsByClassName("kwargs-value")

        kwargs = {}

        for (i = 0; i < keys.length; i++) {
            kwargs[keys[i].value] = values[i].value
        }
        
        return kwargs;
    }

    /*
    1 2 3 4 5 6 7  8  9  10 11 12 13 14  15  16  17  18  19  20   21   22   23   24   25   26   
    1 2 3 4 6 8 12 16 24 32 48 64 96 128 192 256 384 512 768 1024 1536 2048 3072 4096 6144 8192
    */
    function slider_to_text_mapping(x) {
        if (x === 1) {
            return 1;
        } else if (x % 2 === 0) {
            return Math.pow(2, x / 2);
        } else {
            x1 = Math.pow(2, (x - 1) / 2);
            x2 = Math.pow(2, (x + 1) / 2);
            return (x1 + x2)/2;
        }
    }

    function text_to_slider_mapping(x) {
        return Math.log2(x) * 2;
    }

    generation_slider.addEventListener("input", _ => {
        generation_textbox.value = slider_to_text_mapping(Number(generation_slider.value));
    });

    generation_textbox.addEventListener("input", _ => {
        generation_slider.value = text_to_slider_mapping(Number(generation_textbox.value));
    });

    text_window.addEventListener("input", _ => {
        updateTextArea(structuredClone(contextJson));
    });

    stop_button.addEventListener("click", _ => {
        remaining_generations = 0;
    });

    clear_button.addEventListener("click", _ => {
        text_window.value = "";
        text_highlights.innerHTML = "";
    });
});