// Fuzzy, regex-friendly matcher for Select2 timezone search
function buildRegex(term) {
    const escaped = term.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const flexible = escaped.replace(/\s+/g, '.*');
    return new RegExp(flexible, 'i');
}

// Synonym map for common aliases (e.g., Jerusalem → Israel)
const TIMEZONE_SYNONYMS = {
    jerusalem: ['israel'],
    tel_aviv: ['israel'],
    telaviv: ['israel'],
    telaviv_yafo: ['israel'],
    new_york: ['ny', 'newyork', 'new york'],
    los_angeles: ['la', 'losangeles', 'los angeles'],
    chicago: ['chi', 'illinois', 'il'],
    denver: ['mountain', 'colorado', 'co'],
    phoenix: ['arizona', 'az'],
    dallas: ['texas', 'tx', 'dfw'],
    houston: ['texas', 'tx'],
    miami: ['florida', 'fl'],
    toronto: ['canada', 'ca', 'ontario', 'on'],
    vancouver: ['canada', 'ca', 'bc', 'british columbia'],
    mexico_city: ['mexico', 'cdmx'],
    sao_paulo: ['brazil', 'br'],
    buenos_aires: ['argentina'],
    santiago: ['chile'],
    lima: ['peru'],
    bogota: ['colombia'],
    london: ['uk', 'united kingdom', 'england', 'gb', 'great britain'],
    paris: ['france', 'fra'],
    berlin: ['germany', 'deutschland', 'deu'],
    madrid: ['spain', 'es'],
    lisbon: ['portugal', 'pt'],
    rome: ['italy', 'ita'],
    athens: ['greece', 'gr'],
    istanbul: ['turkey', 'tuerkiye', 'turkiye', 'tr'],
    cairo: ['egypt'],
    johannesburg: ['south africa', 'za'],
    nairobi: ['kenya'],
    dubai: ['uae', 'united arab emirates'],
    riyadh: ['saudi', 'saudi arabia', 'ksa'],
    moscow: ['russia', 'ru'],
    kyiv: ['ukraine', 'ua', 'kiev'],
    tehran: ['iran'],
    karachi: ['pakistan', 'pk'],
    delhi: ['india', 'in', 'new delhi'],
    mumbai: ['india', 'in', 'bombay'],
    kolkata: ['india', 'in', 'calcutta'],
    hong_kong: ['hk', 'hongkong', 'hong kong'],
    shanghai: ['china', 'cn'],
    beijing: ['china', 'cn', 'peking'],
    singapore: ['sg', 'sin', 'sing', 'singapore'],
    tokyo: ['japan', 'jp'],
    seoul: ['korea', 'south korea', 'kr'],
    manila: ['philippines', 'ph'],
    bangkok: ['thailand', 'th'],
    hanoi: ['vietnam', 'vn'],
    jakarta: ['indonesia', 'id'],
    sydney: ['australia', 'au', 'nsw'],
    melbourne: ['australia', 'au'],
    brisbane: ['australia', 'au', 'queensland', 'qld'],
    perth: ['australia', 'au', 'wa'],
    auckland: ['new zealand', 'nz'],
    honolulu: ['hawaii', 'hi', 'hon']
};

function buildSearchText(timezone) {
    const parts = timezone.split(/[\/_]/).map(p => p.toLowerCase());
    const extras = [];
    parts.forEach(p => {
        if (TIMEZONE_SYNONYMS[p]) {
            extras.push(...TIMEZONE_SYNONYMS[p]);
        }
    });
    return parts.concat(extras).join(' ');
}

function customMatcher(params, data) {
    if ($.trim(params.term) === '') {
        return data;
    }

    if (typeof data.text === 'undefined') {
        return null;
    }

    const regex = buildRegex(params.term);
    const searchField = (data.element && data.element.dataset.search)
        ? data.element.dataset.search
        : data.text.toLowerCase().replace(/[\/_]/g, ' ');

    if (regex.test(data.text) || regex.test(searchField)) {
        return data;
    }

    if (data.children && data.children.length) {
        const match = $.extend(true, {}, data);
        match.children = [];

        for (let i = 0; i < data.children.length; i++) {
            const child = customMatcher(params, data.children[i]);
            if (child) {
                match.children.push(child);
            }
        }

        if (match.children.length) {
            return match;
        }
    }

    return null;
}

// Function to populate the timezone input with Select2 dropdown
function populateTimezones() {
    const timezoneInputs = $('input[type="timezone"]');
    const timezones = moment.tz.names();
    const timezonesByRegion = {};

    timezones.forEach(timezone => {
        const region = timezone.split('/')[0];
        if (!timezonesByRegion[region]) {
            timezonesByRegion[region] = [];
        }
        timezonesByRegion[region].push(timezone);
    });

    timezoneInputs.each(function() {
        const inputElement = $(this);
        const inputName = inputElement.attr('name');
        const currentValue = inputElement.val();

        const selectElement = $('<select></select>').attr('name', inputName + '_select');
        inputElement.after(selectElement).hide();
        selectElement.empty();

        for (const region in timezonesByRegion) {
            const optgroup = $('<optgroup label="' + region + '"></optgroup>');
            timezonesByRegion[region].forEach(timezone => {
                optgroup.append(
                    $('<option></option>')
                        .attr('value', timezone)
                        .attr('data-search', buildSearchText(timezone))
                        .text(timezone)
                );
            });
            selectElement.append(optgroup);
        }

        selectElement.select2({
            placeholder: 'Select a timezone',
            debug: true,
            dropdownCssClass: 'select2-dropdown--below',
            width: '100%',
            search: true,
            matcher: customMatcher,
            dropdownParent: selectElement.closest('.modal')
        });

        if (currentValue) {
            selectElement.val(currentValue).trigger('change.select2');
            inputElement.val(currentValue);
        }

        selectElement.on('change', function() {
            const selectedValue = $(this).val();
            inputElement.val(selectedValue).trigger('change');
        });
    });
}

$(document).ready(function() {
    populateTimezones();
});

// Initialize timezone autocomplete for settings modal using datalist
function initializeTimezoneSelect2() {
    const settingValueInput = document.getElementById('modalSettingValue');
    const timezoneList = document.getElementById('timezoneList');
    
    if (!settingValueInput || !timezoneList) return;

    // Get all timezones from moment-timezone
    const timezones = moment.tz.names();
    
    // Clear existing options
    timezoneList.innerHTML = '';
    
    // Add all timezones to datalist
    timezones.forEach(tz => {
        const option = document.createElement('option');
        option.value = tz;
        timezoneList.appendChild(option);
    });
}