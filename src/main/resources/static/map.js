function initMap() {
  var geocoder = new google.maps.Geocoder();
  geocoder.geocode({'address': address}, function(results, status) {
    if (status === 'OK') {
      var map = new google.maps.Map(document.getElementById('map'), {
        zoom: 15,
        mapId: `ab35d05ad627f5db`,
        center: results[0].geometry.location
      });

          const advancedMarkerView = new google.maps.marker.AdvancedMarkerView({
            map,
            content: buildContent(property),
            position: results[0].geometry.location,
            title: marker,
          });
          const element = advancedMarkerView.element;
      
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
              <i aria-hidden="true" class="fa fa-icon fa-building" title="$${org.getName()}"></i>
              <span class="fa-sr-only">building</span>
          </div>
          <div class="details">
              <div class="price">Next Minyan</div>
              
              <div class="address" th:each="minyan: ${allminyanim}">${minyan.getType().displayName()[0]}</div>
              <div class="address" th:each="minyan: ${allminyanim}">at</div>
              <div class="address" th:each="minyan: ${allminyanim}">${minyan.getFormattedStartTime().displayName()[0]}</div>
              <div class="features">
              <div>
                  <i aria-hidden="true" class="fa fa-bed fa-lg bed" title="bedroom"></i>
                  <span class="fa-sr-only">bedroom</span>
                  <span>${property.bed}</span>
              </div>
              <div>
                  <i aria-hidden="true" class="fa fa-bath fa-lg bath" title="bathroom"></i>
                  <span class="fa-sr-only">bathroom</span>
                  <span>${property.bath}</span>
              </div>
              <div>
                  <i aria-hidden="true" class="fa fa-ruler fa-lg size" title="size"></i>
                  <span class="fa-sr-only">size</span>
                  <span>${property.size} ft<sup>2</sup></span>
              </div>
              </div>
          </div>
          `;
        return content;
      }
  }
  )
}