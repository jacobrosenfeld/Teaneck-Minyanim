function update(name, mode) {
    var newBox = document.createElement('div');
    newBox.className = "col";

    var nm = `<input type="text" class="form-control" name="${name}-fixed-time" id="${name}-fixed-time" disabled>`;
    var fixed = `<input type="time" class="form-control" name="${name}-fixed-time" id="${name}-fixed-time" required>`;
    var dynamic = `<div class="form-row">
                        <div class="col">
                            <select class="custom-select" name="${name}-zman" id="${name}-zman" required>
                                <option disabled selected>Choose a zman</option>
                                <option value="netz">Netz</option>
                                <option value="chatzot">Chatzot</option>
                                <option value="mincha_gedola">Mincha Gedola</option>
                                <option value="mincha_ketana">Mincha Ketana</option>
                                <option value="plag_hamincha">Plag HaMincha</option>
                                <option value="shekiya">Shekiya</option>
                                <option value="tzet">Tzet Hakochavim</option>
                            </select>
                        </div>
                        <div class="col">
                            <input type="number" class="form-control" name="${name}-zman-offset" id="${name}-zman-offset" value=0 required>
                        </div>
                    </div>`;

    var currentBox = document.getElementById(`${mode}-time-box-${name}`);

    switch(mode) {
        case "nm":
            newBox.id = `nm-time-box-${name}`;
            newBox.innerHTML = nm;
            break;
        case "fixed":
            newBox.id = `fixed-time-box-${name}`;
            newBox.innerHTML = fixed;
            break;
        case "dynamic":
            newBox.id = `dynamic-time-box-${name}`;
            newBox.innerHTML = dynamic;
            break;
        default:
            console.log("Update failed. Invalid mode: " + mode);
            return;
    }

    if (currentBox) {
        currentBox.replaceWith(newBox);
    }
}

function updateAll() {
    update("sunday", document.getElementById("sunday-time-type").value);
    update("monday", document.getElementById("monday-time-type").value);
    update("tuesday", document.getElementById("tuesday-time-type").value);
    update("wednesday", document.getElementById("wednesday-time-type").value);
    update("thursday", document.getElementById("thursday-time-type").value);
    update("friday", document.getElementById("friday-time-type").value);
    update("shabbat", document.getElementById("shabbat-time-type").value);
    update("yt", document.getElementById("yt-time-type").value);
    update("rc", document.getElementById("rc-time-type").value);
    update("chanuka", document.getElementById("chanuka-time-type").value);
    update("rcc", document.getElementById("rcc-time-type").value);
}