import("./frontend/app.mjs").then(({ startApp }) => {
  startApp(document.querySelector("#app"));
});
