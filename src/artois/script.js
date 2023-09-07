

document.addEventListener("DOMContentLoaded", _ => {

    const text = document.getElementById("text-window");
    const generate_button = document.getElementById("generate-button");
    const stop_button = document.getElementById("stop-button");
    const clear_button = document.getElementById("clear-button");
    const generation_slider = document.getElementById("generation-count-slider");
    const generation_textbox = document.getElementById("generation-count-textbox");

    var remaining_generations = 0;

    async function generate() {
        if (remaining_generations > 0) {
            kwargs = fetch_kwargs();

            batch_size = Math.min(Number(kwargs["batch_size"]), remaining_generations)

            kwargs["batch_size"] = batch_size;
            kwargs["context"] = text.value;
            
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

                const json = await response.json();
                text.value = json['context'];
                
            } catch(err) {
                console.error(`Error: ${err}`);
            }

            generate()
        }
    }

    generate_button.addEventListener("click", async _ => {
        remaining_generations = generation_textbox.value;
        generate()
    });

    function fetch_kwargs() {
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

    stop_button.addEventListener("click", _ => {
        remaining_generations = 0;
    });

    clear_button.addEventListener("click", _ => {
        text.value = "";
    });
});