function initMap() {
  var geocoder = new google.maps.Geocoder();
  geocoder.geocode({'address': address}, function(results, status) {
    if (status === 'OK') {
      const center = results[0].geometry.location;
      const map = new google.maps.Map(document.getElementById("map"), {
        zoom: 15,
        center,
        mapId: "ab35d05ad627f5db",
      });

      for (const property of properties) {
        const advancedMarkerView = new google.maps.marker.AdvancedMarkerView({
          map,
          content: buildContent(property),
          position: property.position,
          title: property.description,
        });
        const element = advancedMarkerView.element;
        
        const priceTag = document.createElement("div");
        priceTag.className = "price-tag";
        priceTag.textContent = property.price;
        advancedMarkerView.content.appendChild(priceTag);
        
        ["focus", "pointerenter"].forEach((event) => {
          element.addEventListener(event, () => {
            highlight(advancedMarkerView, property);
          });
        });
        ["blur", "pointerleave"].forEach((event) => {
          element.addEventListener(event, () => {
            unhighlight(advancedMarkerView, property);
          });
        });
        advancedMarkerView.addListener("click", (event) => {
          unhighlight(advancedMarkerView, property);
        });
        google.maps.event.addListener(advancedMarkerView, 'mouseover', function() {
          var extendedInfo = document.createElement("div");
          extendedInfo.className = "extended-info";
          extendedInfo.innerHTML = "The Next Minyan: " + minyantype + " at " + minyantime;
          priceTag.appendChild(extendedInfo);
        });
        google.maps.event.addListener(advancedMarkerView, 'mouseout', function() {
          priceTag.removeChild(extendedInfo);
        });
      }
    } else {
      alert('Geocode was not successful for the following reason: ' + status);
    }
  });
}

function highlight(markerView, property) {
  markerView.content.classList.add("highlight");
  markerView.element.style.zIndex = 1;
}

function unhighlight(markerView, property) {
  markerView.content.classList.remove("highlight");
  markerView.element.style.zIndex = "";
}

function buildContent(property) {
  const content = document.createElement("div");

  content.classList.add("property");
  content.innerHTML = `
    <div class="icon">
        <i aria-hidden="true" class="fa fa-icon fa-${property.type}" title="${property.type}"></i>
        <span class="fa-sr-only">${property.type}</span>
    </div>
    <div class="details">
    `}
